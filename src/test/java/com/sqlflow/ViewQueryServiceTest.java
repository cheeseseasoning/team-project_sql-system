package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.access.dto.ViewDepartmentPolicyInfoDTO;
import com.sqlflow.access.dto.ViewResourceInfoDTO;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.mysql.dto.MySqlViewColumnDTO;
import com.sqlflow.mysql.dto.MySqlViewDefinitionDTO;
import com.sqlflow.mysql.mapper.MySqlViewMapper;
import com.sqlflow.view.dto.ViewDetailResponseDTO;
import com.sqlflow.view.service.ViewQueryService;

/**
 * 뷰 상세의 정책 공개 범위와 잘못된 조회 차단을 검증한다.
 * 실제 Oracle·MySQL에는 접속하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class ViewQueryServiceTest {

    private static final Long ACTOR_ID = 100L;
    private static final Long RESOURCE_ID = 5L;

    private static final String SCHEMA = "sqlflow_demo";
    private static final String OBJECT = "v_active_product_catalog";
    private static final String DDL =
            "CREATE VIEW v_active_product_catalog AS SELECT product_id FROM demo_product";

    @Mock
    private MySqlViewMapper mySqlViewMapper;

    @Mock
    private ViewResourceMapper viewResourceMapper;

    @Mock
    private DataAccessPolicyMapper dataAccessPolicyMapper;

    @InjectMocks
    private ViewQueryService service;

    @Test
    void ordinaryUserReceivesOnlyOwnDepartmentPolicy() {
        prepareViewer(1L, "Y");
        prepareView(true);

        ViewDetailResponseDTO result = detail();

        assertEquals(List.of(1L), visibleDepartmentIds(result));
        assertEquals("BLOCK", result.departmentPolicies().get(0).getExportPolicy());

        assertTrue(result.registered());
        assertEquals(RESOURCE_ID, result.resourceId());
        assertEquals(OBJECT, result.objectName());
        assertEquals("활성 상품 목록", result.resourceName());

        assertEquals(1, result.columns().size());
        assertEquals("product_id", result.columns().get(0).getColumnName());
        assertEquals(DDL, result.createStatement());
    }

    @Test
    void dbTeamUserReceivesAllDepartmentPolicies() {
        prepareViewer(2L, "Y");
        prepareView(true);

        ViewDetailResponseDTO result = detail();

        assertEquals(List.of(1L, 2L, 3L), visibleDepartmentIds(result));

        // DB팀 정책이 없어도 해당 부서는 미등록 상태로 표시한다.
        ViewDepartmentPolicyInfoDTO dbPolicy = result.departmentPolicies().get(1);

        assertEquals(2L, dbPolicy.getDeptId());
        assertNull(dbPolicy.getPolicyId());
    }

    @Test
    void currentDepartmentOverridesOldDepartmentName() {
        UserAccount viewer = prepareViewer(1L, "Y");

        // 로그인 당시 정보처럼 과거 DB팀 이름이 남아 있어도 신뢰하지 않는다.
        viewer.setDeptName("DB팀");
        prepareView(true);

        ViewDetailResponseDTO result = detail();

        assertEquals(List.of(1L), visibleDepartmentIds(result));
        assertEquals("개발팀", viewer.getDeptName());
    }

    @Test
    void unregisteredViewShowsOnlyOwnDepartmentAsUnregistered() {
        prepareViewer(1L, "Y");
        prepareView(false);

        ViewDetailResponseDTO result = detail();

        assertFalse(result.registered());
        assertNull(result.resourceId());
        assertNull(result.resourceName());
        assertEquals(List.of(1L), visibleDepartmentIds(result));
        assertNull(result.departmentPolicies().get(0).getPolicyId());

        assertEquals(DDL, result.createStatement());
        verify(viewResourceMapper, never()).selectDepartmentPolicies(RESOURCE_ID);
    }

    @Test
    void dbTeamCanInspectAllDepartmentsForUnregisteredView() {
        prepareViewer(2L, "Y");
        prepareView(false);

        ViewDetailResponseDTO result = detail();

        assertFalse(result.registered());
        assertEquals(List.of(1L, 2L, 3L), visibleDepartmentIds(result));

        assertTrue(result.departmentPolicies().stream()
                .allMatch(policy -> policy.getPolicyId() == null));
    }

    @Test
    void inactiveResourceRemainsReadableAsMetadata() {
        prepareViewer(1L, "Y");
        prepareView(true);

        // 登録状態の表示と実データへのアクセス許可は別に扱う。
        ViewResourceInfoDTO resource = resource();
        resource.setEnabled("N");

        when(viewResourceMapper.selectResources(SCHEMA))
                .thenReturn(List.of(resource));

        ViewDetailResponseDTO result = detail();

        assertTrue(result.registered());
        assertEquals("N", result.enabled());
        assertEquals(DDL, result.createStatement());
    }

    @Test
    void anonymousUserIsRejectedBeforeDatabaseLookup() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getViewDetail(SCHEMA, OBJECT, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());

        verifyNoInteractions(
                dataAccessPolicyMapper,
                viewResourceMapper,
                mySqlViewMapper);
    }

    @Test
    void inactiveUserIsRejectedBeforeMetadataLookup() {
        when(dataAccessPolicyMapper.selectRequester(ACTOR_ID))
                .thenReturn(account(1L, "N"));

        assertDetailStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(viewResourceMapper, mySqlViewMapper);
    }

    @Test
    void missingUserIsRejectedBeforeMetadataLookup() {
        when(dataAccessPolicyMapper.selectRequester(ACTOR_ID))
                .thenReturn(null);

        assertDetailStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(viewResourceMapper, mySqlViewMapper);
    }

    @Test
    void unknownDepartmentIsRejectedBeforeMetadataLookup() {
        prepareViewer(999L, "Y");

        assertDetailStatus(HttpStatus.FORBIDDEN);

        verifyNoInteractions(mySqlViewMapper);
    }

    @Test
    void blankObjectNameIsRejected() {
        prepareViewer(1L, "Y");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getViewDetail(SCHEMA, " ", ACTOR_ID));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(mySqlViewMapper);
    }

    @Test
    void missingViewOrBaseTableIsRejectedBeforeDefinitionLookup() {
        prepareViewer(1L, "Y");

        when(mySqlViewMapper.countView(SCHEMA, "demo_product"))
                .thenReturn(0);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.getViewDetail(SCHEMA, "demo_product", ACTOR_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(mySqlViewMapper, never()).selectViewColumns(SCHEMA, "demo_product");
        verify(mySqlViewMapper, never()).selectViewDefinition(SCHEMA, "demo_product");
        verify(viewResourceMapper, never()).selectResources(SCHEMA);
    }

    @Test
    void duplicateResourceRegistrationIsRejected() {
        prepareViewer(1L, "Y");

        when(mySqlViewMapper.countView(SCHEMA, OBJECT)).thenReturn(1);
        when(viewResourceMapper.selectResources(SCHEMA))
                .thenReturn(List.of(resource(), resource()));

        assertDetailStatus(HttpStatus.CONFLICT);

        verify(mySqlViewMapper, never()).selectViewColumns(SCHEMA, OBJECT);
        verify(mySqlViewMapper, never()).selectViewDefinition(SCHEMA, OBJECT);
    }

    @Test
    void emptyColumnsAreRejected() {
        prepareViewer(1L, "Y");
        prepareMetadata();

        when(mySqlViewMapper.selectViewColumns(SCHEMA, OBJECT))
                .thenReturn(List.of());

        assertDetailStatus(HttpStatus.CONFLICT);

        verify(viewResourceMapper, never()).selectDepartmentPolicies(RESOURCE_ID);
    }

    @Test
    void missingDefinitionIsRejected() {
        prepareViewer(1L, "Y");
        prepareMetadata();

        when(mySqlViewMapper.selectViewDefinition(SCHEMA, OBJECT))
                .thenReturn(null);

        assertDetailStatus(HttpStatus.CONFLICT);

        verify(viewResourceMapper, never()).selectDepartmentPolicies(RESOURCE_ID);
    }

    @Test
    void blankCreateStatementIsRejected() {
        prepareViewer(1L, "Y");
        prepareMetadata();

        MySqlViewDefinitionDTO definition = new MySqlViewDefinitionDTO();
        definition.setObjectName(OBJECT);
        definition.setCreateStatement(" ");

        when(mySqlViewMapper.selectViewDefinition(SCHEMA, OBJECT))
                .thenReturn(definition);

        assertDetailStatus(HttpStatus.CONFLICT);
    }

    private ViewDetailResponseDTO detail() {
        return service.getViewDetail(SCHEMA, OBJECT, ACTOR_ID);
    }

    private void assertDetailStatus(HttpStatus expectedStatus) {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                this::detail);

        assertEquals(expectedStatus, error.getStatusCode());
    }

    private List<Long> visibleDepartmentIds(ViewDetailResponseDTO result) {
        return result.departmentPolicies().stream()
                .map(ViewDepartmentPolicyInfoDTO::getDeptId)
                .toList();
    }

    private UserAccount prepareViewer(Long deptId, String enabled) {
        UserAccount viewer = account(deptId, enabled);

        when(dataAccessPolicyMapper.selectRequester(ACTOR_ID))
                .thenReturn(viewer);

        when(viewResourceMapper.selectDepartments())
                .thenReturn(List.of(
                        department(1L, "개발팀"),
                        department(2L, "DB팀"),
                        department(3L, "영업1팀")));

        return viewer;
    }

    private void prepareView(boolean registered) {
        prepareMetadata();

        if (registered) {
            when(viewResourceMapper.selectDepartmentPolicies(RESOURCE_ID))
                    .thenReturn(List.of(
                            policy(1L, "개발팀", 18L, "BLOCK"),
                            policy(2L, "DB팀", null, null),
                            policy(3L, "영업1팀", 19L, "ALLOW")));
        } else {
            when(viewResourceMapper.selectResources(SCHEMA))
                    .thenReturn(List.of());
        }
    }

    private void prepareMetadata() {
        when(mySqlViewMapper.countView(SCHEMA, OBJECT)).thenReturn(1);

        when(viewResourceMapper.selectResources(SCHEMA))
                .thenReturn(List.of(resource()));

        MySqlViewColumnDTO column = new MySqlViewColumnDTO();
        column.setOrdinalPosition(1);
        column.setColumnName("product_id");
        column.setColumnType("bigint");
        column.setIsNullable("NO");
        column.setColumnComment("");

        when(mySqlViewMapper.selectViewColumns(SCHEMA, OBJECT))
                .thenReturn(List.of(column));

        MySqlViewDefinitionDTO definition = new MySqlViewDefinitionDTO();
        definition.setObjectName(OBJECT);
        definition.setCreateStatement(DDL);

        when(mySqlViewMapper.selectViewDefinition(SCHEMA, OBJECT))
                .thenReturn(definition);
    }

    private UserAccount account(Long deptId, String enabled) {
        UserAccount account = new UserAccount();

        account.setUserId(ACTOR_ID);
        account.setDeptId(deptId);
        account.setEnabled(enabled);

        return account;
    }

    private ViewDepartmentInfoDTO department(Long deptId, String deptName) {
        ViewDepartmentInfoDTO department = new ViewDepartmentInfoDTO();

        department.setDeptId(deptId);
        department.setDeptName(deptName);

        return department;
    }

    private ViewResourceInfoDTO resource() {
        ViewResourceInfoDTO resource = new ViewResourceInfoDTO();

        resource.setResourceId(RESOURCE_ID);
        resource.setSchemaName(SCHEMA);
        resource.setObjectName(OBJECT);
        resource.setResourceName("활성 상품 목록");
        resource.setDescription("활성 상품 정보를 제공합니다.");
        resource.setEnabled("Y");

        return resource;
    }

    private ViewDepartmentPolicyInfoDTO policy(
            Long deptId, String deptName, Long policyId, String exportPolicy) {

        ViewDepartmentPolicyInfoDTO policy = new ViewDepartmentPolicyInfoDTO();

        policy.setResourceId(RESOURCE_ID);
        policy.setDeptId(deptId);
        policy.setDeptName(deptName);
        policy.setPolicyId(policyId);
        policy.setAccessLevel(policyId == null ? null : "GENERAL");
        policy.setExportPolicy(exportPolicy);
        policy.setPolicyEnabled(policyId == null ? null : "Y");

        return policy;
    }
}