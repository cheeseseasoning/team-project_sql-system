package com.sqlflow.request.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.mysql.mapper.MySqlViewMapper;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;

/** 업무 SQL이나 DB를 실행하지 않고 등록·정정에서 동일한 연결 정책을 적용하는지 확인한다. */
class ViewCreationLinkServiceTest {
    private DepartmentRequestService results;
    private MySqlViewMapper views;
    private MySqlMetadataMapper metadata;
    private JdbcTemplate jdbc;
    private ViewCreationLinkService service;
    private String type;
    private String sql;
    private List<String> otherSuccesses;
    private final java.util.Map<String, String> otherSuccessNotes = new java.util.HashMap<>();
    private List<Row> resources;
    private final List<String> updates = new ArrayList<>();
    private record Row(long id, String schema, String name, Long requestId) { }

    @BeforeEach void setUp() {
        results = mock(DepartmentRequestService.class);
        views = mock(MySqlViewMapper.class);
        metadata = mock(MySqlMetadataMapper.class);
        type = "CREATE";
        sql = "CREATE VIEW demo.v AS SELECT 1;";
        otherSuccesses = List.of();
        otherSuccessNotes.clear();
        resources = List.of(new Row(7L, "demo", "v", null));
        updates.clear();
        jdbc = mock(JdbcTemplate.class, call -> {
            String method = call.getMethod().getName();
            Object[] args = call.getArguments();
            String query = args.length > 0 && args[0] instanceof String text ? text : "";
            if (method.equals("queryForObject")) return query.contains("REQUEST_TYPE") ? type : sql;
            if (method.equals("queryForList")) return otherSuccesses;
            if (method.equals("update")) { updates.add(query); return 1; }
            if (method.equals("query")) {
                @SuppressWarnings("unchecked") RowMapper<Object> mapper = (RowMapper<Object>) args[1];
                List<Object> rows = new ArrayList<>();
                if (query.contains("SELECT V.SQL_CONTENT, CR.RESULT_NOTE")) {
                    for (String text : otherSuccesses) {
                        ResultSet rs = mock(ResultSet.class);
                        when(rs.getString(1)).thenReturn(text);
                        when(rs.getString(2)).thenReturn(otherSuccessNotes.get(text));
                        rows.add(mapper.mapRow(rs, rows.size()));
                    }
                    return rows;
                }
                for (Row row : resources) {
                    if (query.contains("WHERE CREATE_REQUEST_ID") && !Long.valueOf(10L).equals(row.requestId())) continue;
                    if (query.contains("WHERE RESOURCE_ID") && !Long.valueOf(row.id()).equals(args[2])) continue;
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong(1)).thenReturn(row.id());
                    when(rs.getString(2)).thenReturn(row.schema());
                    when(rs.getString(3)).thenReturn(row.name());
                    rows.add(mapper.mapRow(rs, rows.size()));
                }
                return rows;
            }
            return org.mockito.Answers.RETURNS_DEFAULTS.answer(call);
        });
        when(metadata.selectCurrentDatabase()).thenReturn("demo");
        when(metadata.selectObject(anyString(), anyString())).thenAnswer(call -> {
            var object = new MySqlObjectMetadata();
            object.setDatabaseName(call.getArgument(0));
            object.setObjectName(call.getArgument(1));
            object.setObjectType("VIEW");
            return object;
        });
        when(views.countView(anyString(), anyString())).thenReturn(1);
        service = new ViewCreationLinkService(results, views, metadata, mock(DataSource.class));
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    private void register(String status, Long resource) {
        service.register(10L, 20L, 1L, status, "확인", "CANCELLED".equals(status) ? null : 2L, resource, null);
    }
    private void change(String status, Long resource, String decision) {
        service.change(10L, 20L, 1L, status, "DB팀 확인", "CANCELLED".equals(status) ? null : 2L,
                "CORRECTION", "결과 정정", resource, decision);
    }
    private boolean linked() { return updates.stream().anyMatch(text -> text.contains("SET CREATE_REQUEST_ID = ?")); }
    private boolean unlinked() { return updates.stream().anyMatch(text -> text.contains("SET CREATE_REQUEST_ID = NULL")); }

    @Test void otherCreateAndDmlDoNotRequireViewLink() {
        sql = "CREATE TABLE t(id INT);";
        register("COMPLETED", null);
        type = "INSERT";
        register("COMPLETED", null);
        assertTrue(updates.isEmpty());
        verifyNoInteractions(views);
    }
    @Test void viewSuccessRequiresMatchingExistingView() {
        assertThrows(ResponseStatusException.class, () -> register("COMPLETED", null));
        assertFalse(linked());
        register("COMPLETED", 7L);
        assertTrue(linked());
        verify(results, times(2)).registerChangeSqlResult(10L, 20L, 1L, "COMPLETED", "확인", 2L);
    }
    @Test void rejectsWrongViewAndDoesNotTrustManualOverrideForKnownTarget() {
        sql = "CREATE VIEW demo.other_view AS SELECT 1;";
        assertThrows(ResponseStatusException.class, () -> change("COMPLETED", 7L, "VIEW"));
        assertFalse(linked());
    }
    @Test void rejectsMissingActualViewAndAnotherRequestOwnership() {
        when(views.countView("demo", "v")).thenReturn(0);
        assertThrows(ResponseStatusException.class, () -> register("COMPLETED", 7L));
        assertFalse(linked());
        when(views.countView("demo", "v")).thenReturn(1);
        doReturn(0).when(jdbc).update(startsWith("UPDATE DATA_RESOURCE SET CREATE_REQUEST_ID = ?"), any(Object[].class));
        assertThrows(ResponseStatusException.class, () -> register("COMPLETED", 7L));
    }
    @Test void failureToSuccessCorrectionUsesSameLinkValidation() {
        change("COMPLETED", 7L, null);
        assertTrue(linked());
        verify(results).changeSqlResult(10L, 20L, 1L, "COMPLETED", "DB팀 확인", 2L, "CORRECTION", "결과 정정");
    }
    @Test void failureAndCancellationWithoutExistingLinkNeedNoResource() {
        register("FAILED", null);
        register("CANCELLED", null);
        assertTrue(updates.isEmpty());
    }
    @Test void correctionReleasesOnlyMatchingRequestLinkAndPreservesMetadata() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        change("FAILED", null, null);
        assertTrue(unlinked());
        assertEquals(1, updates.size());
        assertFalse(updates.getFirst().contains("VIEW_COLUMN_METADATA"));
    }
    @Test void otherSuccessfulSqlForSameViewKeepsLink() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        otherSuccesses = List.of("CREATE OR REPLACE VIEW demo.v AS SELECT 2;");
        change("FAILED", null, null);
        assertFalse(unlinked());
    }
    @Test void unrelatedSuccessDoesNotPreventReleaseButUnknownSuccessDoes() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        otherSuccesses = List.of("CREATE TABLE t(id INT);");
        change("FAILED", null, null);
        assertTrue(unlinked());
        updates.clear();
        otherSuccesses = List.of("CREATE UNKNOWN x;");
        assertThrows(ResponseStatusException.class, () -> change("FAILED", null, null));
        assertFalse(unlinked());
    }
    @Test void doesNotReleaseUnrelatedViewLink() {
        resources = List.of(new Row(7, "demo", "other_view", 10L));
        change("CANCELLED", null, null);
        assertFalse(unlinked());
    }
    @Test void unknownTargetRequiresExplicitConfirmationAndAuditsIt() {
        sql = "CREATE UNKNOWN x;";
        assertThrows(ResponseStatusException.class, () -> register("COMPLETED", 7L));
        change("COMPLETED", 7L, "VIEW");
        assertTrue(linked());
        verify(jdbc).update("UPDATE DB_CHANGE_SQL_RESULT SET RESULT_NOTE = ? WHERE QUERY_ID = ?", "[생성 대상 확인: 뷰] DB팀 확인", 20L);
    }
    @Test void unknownOtherTargetCanBeConfirmedWithoutLink() {
        sql = "CREATE UNKNOWN x;";
        change("COMPLETED", null, "OTHER");
        assertFalse(linked());
        assertFalse(unlinked());
    }
    @Test void unknownPreviouslyLinkedTargetNeedsExplicitReleaseConfirmation() {
        sql = "CREATE UNKNOWN x;";
        resources = List.of(new Row(7, "demo", "v", 10L));
        assertThrows(ResponseStatusException.class, () -> change("FAILED", null, null));
        assertFalse(unlinked());
        change("FAILED", 7L, "VIEW");
        assertTrue(unlinked());
    }
    @Test void unqualifiedTargetUsesConfiguredBusinessDatabase() {
        sql = "CREATE VIEW v AS SELECT 1;";
        register("COMPLETED", 7L);
        verify(metadata).selectObject("demo", "v");
        assertTrue(linked());
    }
    @Test void registerAndChangeUseManagementTransaction() throws Exception {
        for (var method : ViewCreationLinkService.class.getDeclaredMethods()) {
            if (!method.getName().equals("register") && !method.getName().equals("change")) continue;
            assertEquals("managementTransactionManager", method.getAnnotation(Transactional.class).value());
        }
    }
    @Test void linkValidationFailureRollsBackOuterResultTransaction() {
        var manager = mock(org.springframework.transaction.PlatformTransactionManager.class);
        var transaction = new org.springframework.transaction.support.SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        var beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("managementTransactionManager", manager);
        var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor();
        interceptor.setTransactionManager(manager);
        interceptor.setTransactionAttributeSource(new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource());
        interceptor.setBeanFactory(beans);
        interceptor.afterPropertiesSet();
        var factory = new org.springframework.aop.framework.ProxyFactory(service);
        factory.addAdvice(interceptor);
        var proxy = (ViewCreationLinkService) factory.getProxy();
        assertThrows(ResponseStatusException.class,
                () -> proxy.register(10L, 20L, 1L, "COMPLETED", "확인", 2L, null, null));
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void unknownOtherInitialResultDoesNotAffectOtherLinkedViews() {
        sql = "CREATE UNKNOWN x;";
        resources = List.of(new Row(7, "demo", "v", 10L));
        service.register(10L, 20L, 1L, "COMPLETED", "DB팀 확인", 2L, null, "OTHER");
        assertFalse(linked());
        assertFalse(unlinked());
    }
    @Test void unknownTargetCannotLeavePreviousLinkBehindWhenChoosingAnotherView() {
        sql = "CREATE UNKNOWN x;";
        resources = List.of(new Row(7, "demo", "v", 10L), new Row(8, "demo", "other", null));
        assertThrows(ResponseStatusException.class, () -> change("COMPLETED", 8L, "VIEW"));
        assertFalse(linked());
    }
    @Test void existingAuthorizationFailureStopsBeforeLinkQueries() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "권한 없음"))
                .when(results).registerChangeSqlResult(10L, 20L, 1L, "COMPLETED", "확인", 2L);
        assertThrows(ResponseStatusException.class, () -> register("COMPLETED", 7L));
        verifyNoInteractions(jdbc);
        assertTrue(updates.isEmpty());
    }

    @Test void confirmedNonViewSiblingDoesNotPreventKnownViewRelease() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        String reviewed = "CREATE UNKNOWN reviewed_non_view;";
        otherSuccesses = List.of(reviewed);
        otherSuccessNotes.put(reviewed, "[생성 대상 확인: 뷰 이외 객체] DB팀 확인");
        change("FAILED", null, null);
        assertTrue(unlinked());
    }

    @Test void unrelatedUnknownItemCanFailWithoutChangingOtherSuccessfulViewLink() {
        sql = "CREATE UNKNOWN unrelated_item;";
        resources = List.of(new Row(7, "demo", "v", 10L));
        otherSuccesses = List.of("CREATE VIEW demo.v AS SELECT 1;");
        change("FAILED", null, null);
        assertTrue(updates.isEmpty());
        verify(results).changeSqlResult(10L, 20L, 1L, "FAILED", "DB팀 확인", 2L, "CORRECTION", "결과 정정");
    }

    @Test void unrelatedUnknownItemCanCancelWhenAllLinksHaveOtherKnownSources() {
        sql = "CREATE UNKNOWN unrelated_item;";
        resources = List.of(new Row(7, "demo", "v", 10L), new Row(8, "demo", "second", 10L));
        otherSuccesses = List.of("CREATE VIEW demo.v AS SELECT 1;", "CREATE VIEW demo.second AS SELECT 2;");
        change("CANCELLED", null, null);
        assertTrue(updates.isEmpty());
    }

    @Test void unknownItemStillRequiresConfirmationWhenAnyLinkIsNotSupported() {
        sql = "CREATE UNKNOWN uncertain_item;";
        resources = List.of(new Row(7, "demo", "v", 10L), new Row(8, "demo", "unidentified", 10L));
        otherSuccesses = List.of("CREATE VIEW demo.v AS SELECT 1;");
        assertThrows(ResponseStatusException.class, () -> change("FAILED", null, null));
        assertFalse(unlinked());
    }

    @Test void freeTextOrIncompleteNonViewNotesDoNotBypassUnknownSourceCheck() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        String uncertain = "CREATE UNKNOWN uncertain_source;";
        otherSuccesses = List.of(uncertain);
        for (String note : List.of("뷰 이외 객체라고 예상함", "[생성 대상 확인: 뷰 이외 객체] ", "[생성 대상 확인: 뷰] DB팀 확인")) {
            otherSuccessNotes.put(uncertain, note);
            assertThrows(ResponseStatusException.class, () -> change("FAILED", null, null));
            assertFalse(unlinked());
        }
    }

    @Test void knownViewStillMaintainsLinkEvenIfItsNoteLooksLikeNonViewConfirmation() {
        resources = List.of(new Row(7, "demo", "v", 10L));
        String knownView = "CREATE VIEW demo.v AS SELECT 1;";
        otherSuccesses = List.of(knownView);
        otherSuccessNotes.put(knownView, "[생성 대상 확인: 뷰 이외 객체] 잘못된 메모");
        change("FAILED", null, null);
        assertFalse(unlinked());
    }

    @Test void explicitWrongResourceIsNotIgnoredWhenOtherSourcesSupportLinks() {
        sql = "CREATE UNKNOWN uncertain_item;";
        resources = List.of(new Row(7, "demo", "v", 10L));
        otherSuccesses = List.of("CREATE VIEW demo.v AS SELECT 1;");
        assertThrows(ResponseStatusException.class, () -> change("FAILED", 99L, "VIEW"));
        assertFalse(unlinked());
    }

}
