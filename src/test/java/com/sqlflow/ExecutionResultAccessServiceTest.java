package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.domain.DataAccessPolicy;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.mapper.ExecutionResultMapper;
import com.sqlflow.execution.service.ExecutionResultAccessService;

@ExtendWith(MockitoExtension.class)
class ExecutionResultAccessServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long DEPT_ID = 20L;
    private static final Long QUERY_ID = 30L;

    private static final QueryObjectName SALES =
            new QueryObjectName("biz_data", "sales");

    private static final QueryObjectName CUSTOMERS =
            new QueryObjectName("biz_data", "customers");

    @Mock
    private ExecutionResultMapper executionResultMapper;

    @Mock
    private DataAccessPolicyMapper dataAccessPolicyMapper;

    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private ExecutionResultAccessService service;

    @Test
    void generalBlockAllowsPreviewButRejectsDownload() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(SALES),
                policy(1L, SALES, "GENERAL", "BLOCK"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));

        // 다운로드 거부가 미리보기 권한을 바꾸면 안 된다.
        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));
    }

    @Test
    void generalAllowPermitsPreviewAndDownload() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(SALES),
                policy(1L, SALES, "GENERAL", "ALLOW"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertDoesNotThrow(
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void sensitiveWithoutRoleRejectsPreviewAndDownload() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE", "ALLOW"));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void sensitiveRoleWithBlockAllowsOnlyPreview() {
        prepareUser(List.of("SENSITIVE_DATA_READER"));
        preparePolicies(
                Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE", "BLOCK"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void sensitiveRoleWithAllowPermitsDownload() {
        prepareUser(List.of("SENSITIVE_DATA_READER"));
        preparePolicies(
                Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE", "ALLOW"));

        assertDoesNotThrow(
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void oneBlockedResourceRejectsWholeDownload() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(SALES, CUSTOMERS),
                policy(1L, SALES, "GENERAL", "ALLOW"),
                policy(2L, CUSTOMERS, "GENERAL", "BLOCK"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void missingPolicyForOneResourceRejectsBothOperations() {
        prepareUser(List.of());

        // SQL은 두 대상을 조회하지만 현재 부서 정책은 하나뿐이다.
        preparePolicies(
                Set.of(SALES, CUSTOMERS),
                policy(1L, SALES, "GENERAL", "ALLOW"));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void dbAdminBypassesDataAndExportPolicies() {
        assertPrivilegedRole("DB_ADMIN");
    }

    @Test
    void dbApproverBypassesDataAndExportPolicies() {
        assertPrivilegedRole("DB_APPROVER");
    }

    @Test
    void disabledUserIsRejectedBeforeRoleBypass() {
        UserAccount actor = activeUser();
        actor.setEnabled("N");

        when(dataAccessPolicyMapper.selectRequester(USER_ID))
                .thenReturn(actor);

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requireDownload(USER_ID, QUERY_ID));

        verifyNoInteractions(
                userAccountMapper,
                executionResultMapper);
    }

    @Test
    void unknownExportPolicyDoesNotBlockPreviewButRejectsDownload() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(SALES),
                policy(1L, SALES, "GENERAL", "UNKNOWN"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertRejected(
                HttpStatus.CONFLICT,
                () -> service.requireDownload(USER_ID, QUERY_ID));
    }

    @Test
    void duplicatePoliciesAreRejected() {
        prepareUser(List.of());
        preparePolicies(
                Set.of(SALES),
                policy(1L, SALES, "GENERAL", "ALLOW"),
                policy(1L, SALES, "GENERAL", "BLOCK"));

        assertRejected(
                HttpStatus.CONFLICT,
                () -> service.requirePreview(USER_ID, QUERY_ID));
    }

    @Test
    void missingStoredTargetsAreRejected() {
        prepareUser(List.of());

        when(executionResultMapper.selectResultTargets(QUERY_ID))
                .thenReturn(List.of());

        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requirePreview(USER_ID, QUERY_ID));
    }

    @Test
    void missingLoginIsRejectedBeforeDatabaseAccess() {
        assertRejected(
                HttpStatus.UNAUTHORIZED,
                () -> service.requirePreview(null, QUERY_ID));

        verifyNoInteractions(
                executionResultMapper,
                dataAccessPolicyMapper,
                userAccountMapper);
    }

    @Test
    void revokedSensitiveRoleIsAppliedToNextRequest() {
        when(dataAccessPolicyMapper.selectRequester(USER_ID))
                .thenReturn(activeUser());

        when(userAccountMapper.selectRoleCodes(USER_ID))
                .thenReturn(
                        List.of("SENSITIVE_DATA_READER"),
                        List.of());

        preparePolicies(
                Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE", "BLOCK"));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        // 기존 로그인 세션이 있어도 다음 요청은 현재 역할로 판단한다.
        assertRejected(
                HttpStatus.FORBIDDEN,
                () -> service.requirePreview(USER_ID, QUERY_ID));
    }

    private void assertPrivilegedRole(String role) {
        prepareUser(List.of(role));

        assertDoesNotThrow(
                () -> service.requirePreview(USER_ID, QUERY_ID));

        assertDoesNotThrow(
                () -> service.requireDownload(USER_ID, QUERY_ID));

        // 관리자 예외에서는 자원별 정책 조회 경로에 진입하지 않는다.
        verifyNoInteractions(executionResultMapper);
    }

    private void prepareUser(List<String> roles) {
        when(dataAccessPolicyMapper.selectRequester(USER_ID))
                .thenReturn(activeUser());

        when(userAccountMapper.selectRoleCodes(USER_ID))
                .thenReturn(roles);
    }

    private UserAccount activeUser() {
        UserAccount actor = new UserAccount();

        actor.setUserId(USER_ID);
        actor.setDeptId(DEPT_ID);
        actor.setEnabled("Y");

        return actor;
    }

    private void preparePolicies(
            Set<QueryObjectName> targets,
            DataAccessPolicy... policies) {

        when(executionResultMapper.selectResultTargets(QUERY_ID))
                .thenReturn(List.copyOf(targets));

        when(dataAccessPolicyMapper.selectPolicies(DEPT_ID, targets))
                .thenReturn(List.of(policies));
    }

    private DataAccessPolicy policy(
            Long resourceId,
            QueryObjectName target,
            String accessLevel,
            String exportPolicy) {

        DataAccessPolicy policy = new DataAccessPolicy();

        policy.setResourceId(resourceId);
        policy.setPolicyId(resourceId + 100L);
        policy.setOwner(target.owner());
        policy.setObjectName(target.objectName());

        // 조회 허용과 CSV 다운로드 정책을 구분한다.
        policy.setAccessDecision("ALLOW");
        policy.setAccessLevel(accessLevel);
        policy.setExportPolicy(exportPolicy);

        return policy;
    }

    private void assertRejected(
            HttpStatus expectedStatus,
            Executable action) {

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                action);

        assertEquals(expectedStatus, error.getStatusCode());
    }
}