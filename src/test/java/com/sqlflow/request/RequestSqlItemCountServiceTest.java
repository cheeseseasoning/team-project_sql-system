package com.sqlflow.request;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.*;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.*;

/** 실제 서비스 경로로 개수를 검사한다. 저장·결재는 대역이며 실제 DB를 변경하지 않는다. */
@ExtendWith(MockitoExtension.class)
class RequestSqlItemCountServiceTest {
    @Mock RequestMapper mapper;
    @Mock ObjectProvider<ApprovalLineCreator> approvalProvider;
    @Mock ApprovalLineCreator approval;
    @Mock RequestSqlValidationService selectValidation;
    @Mock DmlSqlValidationService dmlValidation;
    @Captor private ArgumentCaptor<List<ApprovalTargetRequest>> approvalTargetsCaptor;
    private RequestService service;

    @BeforeEach void setUp() {
        var basic = new SqlBasicValidator();
        service = new RequestService(mapper, approvalProvider, selectValidation,
                new ViewCreateSqlValidationService(basic), dmlValidation, basic);
    }

    /** 테이블·뷰·인덱스 모두 CREATE 하나로 작성·수정·제출·재기안할 수 있어야 한다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE product (id BIGINT PRIMARY KEY);",
            "CREATE VIEW product_view AS SELECT id FROM product;",
            "CREATE INDEX idx_product_id ON product (id);"
    })
    void preservesCreateObjectWorkflowsWithOneStoredType(String sql) {
        var input = new ViewCreateDraftRequest();
        input.setTitle("객체 생성"); input.setPurpose("통합 경로 확인");
        var entry = new ViewCreateDraftRequest.SqlEntry();
        entry.setQueryTitle("객체 생성 SQL"); entry.setSqlText(sql);
        input.setItems(List.of(entry));

        when(mapper.selectNextRequestId()).thenReturn(2L);
        when(mapper.insertRequest(any(QueryRequest.class))).thenReturn(1);
        when(mapper.insertViewSql(eq(2L), eq(1), anyString(), eq(sql))).thenReturn(1);
        assertEquals(2L, service.createViewDraft(input, 1L, 1L));

        when(mapper.selectMyRequestStatusForUpdate(2L, 1L)).thenReturn("DRAFT");
        when(mapper.selectMyRequestDetail(2L, 1L)).thenReturn(detail("CREATE", "DRAFT"));
        when(mapper.updateDraft(any(QueryRequest.class))).thenReturn(1);
        service.updateViewDraft(2L, input, 1L);

        var item = new RequestItemResponse();
        item.setQueryId(101L); item.setQueryOrder(1); item.setSqlText(sql); item.setQueryTitle("객체 생성 SQL");
        when(mapper.selectMyViewSqlItems(2L, 1L)).thenReturn(List.of(item));
        when(approvalProvider.getIfAvailable()).thenReturn(approval);
        when(mapper.submitDraft(2L, 1L)).thenReturn(1);
        var submit = new RequestSubmitRequest(); submit.setApproverIds(List.of(3L, 4L));
        service.submitRequest(2L, submit, 1L);
        verifyApprovalTargets(submit.getApproverIds());

        when(mapper.selectMyRequestStatusForUpdate(9L, 1L)).thenReturn("REJECTED");
        when(mapper.selectMyRequestDetail(9L, 1L)).thenReturn(detail("CREATE", "REJECTED"));
        var redraft = new RequestDraftCreateRequest();
        redraft.setTitle("객체 생성 재기안"); redraft.setPurpose("통합 경로 확인");
        redraft.setSqls(List.of(sql)); redraft.setSqlTitles(List.of("객체 생성 SQL"));
        assertEquals(2L, service.redraftRequest(9L, redraft, 1L, 1L));

        var requests = org.mockito.ArgumentCaptor.forClass(QueryRequest.class);
        verify(mapper, times(2)).insertRequest(requests.capture());
        assertTrue(requests.getAllValues().stream().allMatch(request -> "CREATE".equals(request.getRequestType())));
        verify(mapper, times(3)).insertViewSql(2L, 1, "객체 생성 SQL", sql);
        verifyNoInteractions(selectValidation, dmlValidation);
    }

    @ParameterizedTest
    @CsvSource({"CREATE,10", "CREATE,11"})
    void validatesTenCreateTextsAndRejectsEleven(String type, int count) {
        if (count == 11) {
            assertBadRequest(() -> validateTexts(type, count));
        } else {
            var results = validateTexts(type, count);
            assertEquals(10, results.size());
            assertTrue(results.stream().allMatch(r -> r.getStatus() == ViewCreateSqlValidationResultDTO.Status.VALID));
        }
        verifyNoInteractions(mapper, approvalProvider, approval);
    }

    private List<ViewCreateSqlValidationResultDTO> validateTexts(String type, int count) {
        return service.validateViewSqlTexts(1L, sqls(type, count));
    }

    @ParameterizedTest(name = "{0} {1}: {2}개")
    @MethodSource("createCases")
    void createsOrUpdatesTenItemsAndRejectsEleven(String type, boolean update, int count) {
        if (update && count == 10) {
            when(mapper.selectMyRequestStatusForUpdate(2L, 1L)).thenReturn("DRAFT");
            when(mapper.selectMyRequestDetail(2L, 1L)).thenReturn(detail(type, "DRAFT"));
        }
        if (count == 10) {
            if (update) when(mapper.updateDraft(any(QueryRequest.class))).thenReturn(1);
            else {
                when(mapper.selectNextRequestId()).thenReturn(2L);
                when(mapper.insertRequest(any(QueryRequest.class))).thenReturn(1);
            }
            when(mapper.insertViewSql(eq(2L), anyInt(), anyString(), anyString())).thenReturn(1);
        }
        Runnable action = () -> {
            var input = new ViewCreateDraftRequest(); input.setTitle("개수 검사"); input.setPurpose("경계값");
            input.setItems(IntStream.range(0, count).mapToObj(i -> {
                var item = new ViewCreateDraftRequest.SqlEntry(); item.setQueryTitle("SQL " + (i + 1));
                item.setSqlText(sqls(type, count).get(i)); return item;
            }).toList());
            if (update) service.updateViewDraft(2L, input, 1L); else service.createViewDraft(input, 1L, 1L);
        };
        if (count == 11) {
            assertBadRequest(action);
            verify(mapper, never()).insertViewSql(anyLong(), anyInt(), anyString(), anyString());
            verify(mapper, never()).updateDraft(any());
        } else {
            action.run();
            verifyStoredOrder();
        }
    }

    static Stream<Arguments> createCases() {
        return Stream.of("CREATE").flatMap(type -> Stream.of(false, true)
                .flatMap(update -> IntStream.of(10, 11).mapToObj(count -> Arguments.of(type, update, count))));
    }

    @ParameterizedTest
    @ValueSource(ints = {10, 11})
    void validatesEverySavedCreateItemAndRejectsEleven(int count) {
        when(mapper.selectMyRequestDetail(2L, 1L)).thenReturn(detail("CREATE", "DRAFT"));
        when(mapper.selectMyViewSqlItems(2L, 1L)).thenReturn(items("CREATE", count));
        if (count == 11) assertBadRequest(() -> service.validateViewDraftSql(2L, 1L));
        else {
            var result = service.validateViewDraftSql(2L, 1L);
            assertEquals(10, result.size());
            assertTrue(result.values().stream().allMatch(r -> r.getStatus() == ViewCreateSqlValidationResultDTO.Status.VALID));
        }
        verifyNoInteractions(approvalProvider, approval, selectValidation);
    }

    @ParameterizedTest(name = "제출 {0}: {1}개")
    @MethodSource("submissionCases")
    void submitsTenAndStopsElevenBeforeApproval(String type, int count) {
        when(mapper.selectMyRequestStatusForUpdate(2L, 1L)).thenReturn("DRAFT");
        when(mapper.selectMyRequestDetail(2L, 1L)).thenReturn(detail(type, "DRAFT"));
        var items = items(type, count);
        if (type.equals("SELECT")) when(mapper.selectMyRequestItems(2L, 1L)).thenReturn(items);
        else when(mapper.selectMyViewSqlItems(2L, 1L)).thenReturn(items);
        var input = new RequestSubmitRequest(); input.setApproverIds(List.of(3L, 4L));
        if (count == 11) {
            assertBadRequest(() -> service.submitRequest(2L, input, 1L));
            verifyNoInteractions(approvalProvider, approval, selectValidation, dmlValidation);
            verify(mapper, never()).submitDraft(anyLong(), anyLong());
            return;
        }
        when(approvalProvider.getIfAvailable()).thenReturn(approval);
        when(mapper.submitDraft(2L, 1L)).thenReturn(1);
        if (type.equals("SELECT")) {
            when(selectValidation.validateForSubmission(1L, items)).thenReturn(items.stream()
                    .map(item -> new RequestSqlValidationResult(item.getQueryId(), "VALID", "통과", "GENERAL", List.of())).toList());
            when(mapper.updateSubmittedItemValidation(eq(2L), anyLong(), eq("GENERAL"))).thenReturn(1);
            when(mapper.updateRequestClass(2L, 1L, "GENERAL")).thenReturn(1);
        } else if (isDml(type)) {
            when(dmlValidation.validateForReview(eq(1L), eq(DmlSqlValidationService.SqlType.valueOf(type)), anyString()))
                    .thenReturn(new DmlSqlValidationService.ValidationResult(DmlSqlValidationService.Status.VALID, "통과"));
        }
        service.submitRequest(2L, input, 1L);
        verifyApprovalTargets(input.getApproverIds());
        verify(mapper).submitDraft(2L, 1L);
        if (isDml(type)) verify(dmlValidation, times(10)).validateForReview(eq(1L), eq(DmlSqlValidationService.SqlType.valueOf(type)), anyString());
    }

    static Stream<Arguments> submissionCases() {
        return Stream.of("SELECT", "CREATE", "INSERT", "UPDATE", "DELETE")
                .flatMap(type -> IntStream.of(10, 11).mapToObj(count -> Arguments.of(type, count)));
    }

    @ParameterizedTest(name = "재기안 {0}: {1}개")
    @MethodSource("redraftCases")
    void redraftsTenAndRejectsElevenBeforeSavingSql(String type, int count) {
        when(mapper.selectMyRequestStatusForUpdate(9L, 1L)).thenReturn("REJECTED");
        when(mapper.selectMyRequestDetail(9L, 1L)).thenReturn(detail(type, "REJECTED"));
        when(mapper.selectNextRequestId()).thenReturn(2L);
        when(mapper.insertRequest(any(QueryRequest.class))).thenReturn(1);
        var input = new RequestDraftCreateRequest(); input.setTitle("재기안 개수 검사"); input.setPurpose("경계값");
        input.setSqls(sqls(type, count));
        input.setSqlTitles(IntStream.rangeClosed(1, count).mapToObj(i -> "SQL " + i).toList());
        if (count == 10) {
            when(mapper.insertViewSql(eq(2L), anyInt(), anyString(), anyString())).thenReturn(1);
            if (isDml(type)) when(dmlValidation.validate(eq(DmlSqlValidationService.SqlType.valueOf(type)), anyString()))
                    .thenReturn(new DmlSqlValidationService.ValidationResult(DmlSqlValidationService.Status.VALID, "통과"));
            assertEquals(2L, service.redraftRequest(9L, input, 1L, 1L));
            verifyStoredOrder();
        } else {
            assertBadRequest(() -> service.redraftRequest(9L, input, 1L, 1L));
            verify(mapper, never()).insertViewSql(anyLong(), anyInt(), anyString(), anyString());
        }
    }

    static Stream<Arguments> redraftCases() {
        return Stream.of("CREATE", "INSERT", "UPDATE", "DELETE")
                .flatMap(type -> IntStream.of(10, 11).mapToObj(count -> Arguments.of(type, count)));
    }

    private void verifyApprovalTargets(List<Long> expectedIds) {
        verify(approval).createApprovalLineWithTargets(eq(2L), eq(1L), approvalTargetsCaptor.capture());
        var targets = approvalTargetsCaptor.getValue();
        assertEquals(expectedIds, targets.stream().map(ApprovalTargetRequest::getApproverId).toList());
        assertTrue(targets.stream().allMatch(target -> "USER".equals(target.getTargetType())
                && target.getApproverDeptId() == null));
    }

    private void verifyStoredOrder() {
        var order = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(mapper, times(10)).insertViewSql(eq(2L), order.capture(), anyString(), anyString());
        assertEquals(IntStream.rangeClosed(1, 10).boxed().toList(), order.getAllValues());
    }

    private RequestDetailResponse detail(String type, String status) {
        var detail = new RequestDetailResponse(); detail.setRequestId(2L); detail.setRequestType(type);
        detail.setRequestStatus(status); detail.setTitle("개수 검사"); detail.setPurpose("경계값"); return detail;
    }

    private boolean isDml(String type) {
        return List.of("INSERT", "UPDATE", "DELETE").contains(type);
    }

    private List<RequestItemResponse> items(String type, int count) {
        var sqls = sqls(type, count);
        return IntStream.range(0, count).mapToObj(i -> {
            var item = new RequestItemResponse(); item.setQueryId(101L + i); item.setQueryOrder(i + 1);
            item.setSqlText(sqls.get(i)); item.setQueryTitle("SQL " + (i + 1)); return item;
        }).toList();
    }

    private List<String> sqls(String type, int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> switch (type) {
            case "SELECT" -> "SELECT id FROM t;";
            case "CREATE" -> "CREATE TABLE t" + i + " (id INT);";
            case "INSERT" -> "INSERT INTO t VALUES (" + i + ");";
            case "UPDATE" -> "UPDATE t SET id=" + i + " WHERE id=0;";
            case "DELETE" -> "DELETE FROM t WHERE id=" + i + ";";
            default -> throw new IllegalArgumentException(type);
        }).toList();
    }

    private void assertBadRequest(Runnable action) {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class, action::run).getStatusCode());
    }
}
