package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.domain.DataAccessPolicy;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.execution.domain.QueryObjectName;

@ExtendWith(MockitoExtension.class)
class DataAccessValidationServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long DEPT_ID = 20L;

    // 실제 DB에 존재할 필요가 없는 단위 테스트용 객체다.
    private static final QueryObjectName SALES =
            new QueryObjectName("BIZ_DATA", "SALES");

    private static final QueryObjectName CUSTOMERS =
            new QueryObjectName("BIZ_DATA", "CUSTOMERS");

    @Mock
    private DataAccessPolicyMapper policyMapper;

    @Mock
    private UserAccountMapper userAccountMapper;

    @InjectMocks
    private DataAccessValidationService service;

    @Test
    void generalPolicyAllowsRequestWithoutSensitiveRole() {
        prepareRequester();
        preparePolicies(Set.of(SALES),
                policy(1L, SALES, "GENERAL"));

        var result = service.validate(USER_ID, Set.of(SALES));

        assertEquals("GENERAL", result.accessLevel());
        assertEquals(Set.of(1L), result.resourceIds());

        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void sensitivePolicyRequiresSensitiveRole() {
        prepareRequester();
        preparePolicies(Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE"));

        when(userAccountMapper.selectRoleCodes(USER_ID))
                .thenReturn(List.of("DB_APPROVER", "DB_ADMIN"));

        assertRejected(HttpStatus.FORBIDDEN, Set.of(CUSTOMERS));
    }

    @Test
    void sensitivePolicyAllowsUserWithSensitiveRole() {
        prepareRequester();
        preparePolicies(Set.of(CUSTOMERS),
                policy(2L, CUSTOMERS, "SENSITIVE"));

        when(userAccountMapper.selectRoleCodes(USER_ID))
                .thenReturn(List.of("SENSITIVE_DATA_READER"));

        var result = service.validate(USER_ID, Set.of(CUSTOMERS));

        assertEquals("SENSITIVE", result.accessLevel());
        assertEquals(Set.of(2L), result.resourceIds());
    }

    @Test
    void missingPolicyIsRejectedBeforeRoleCheck() {
        prepareRequester();
        preparePolicies(Set.of(SALES));

        assertRejected(HttpStatus.FORBIDDEN, Set.of(SALES));

        // 부서 정책이 없으면 개인 역할로 우회할 수 없다.
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void joinIsRejectedWhenOneTargetHasNoPolicy() {
        prepareRequester();

        Set<QueryObjectName> targets = Set.of(SALES, CUSTOMERS);
        preparePolicies(targets, policy(1L, SALES, "GENERAL"));

        assertRejected(HttpStatus.FORBIDDEN, targets);
    }

    @Test
    void mixedTargetsAreClassifiedAsSensitive() {
        prepareRequester();

        Set<QueryObjectName> targets = Set.of(SALES, CUSTOMERS);
        preparePolicies(targets,
                policy(1L, SALES, "GENERAL"),
                policy(2L, CUSTOMERS, "SENSITIVE"));

        when(userAccountMapper.selectRoleCodes(USER_ID))
                .thenReturn(List.of("SENSITIVE_DATA_READER"));

        var result = service.validate(USER_ID, targets);

        assertEquals("SENSITIVE", result.accessLevel());
        assertEquals(Set.of(1L, 2L), result.resourceIds());
    }

    @Test
    void disabledRequesterIsRejected() {
        UserAccount requester = requester();
        requester.setEnabled("N");

        when(policyMapper.selectRequester(USER_ID))
                .thenReturn(requester);

        assertRejected(HttpStatus.FORBIDDEN, Set.of(SALES));
        verifyNoInteractions(userAccountMapper);
    }

    @Test
    void unknownAccessLevelIsRejected() {
        prepareRequester();
        preparePolicies(Set.of(SALES),
                policy(1L, SALES, "UNKNOWN"));

        assertRejected(HttpStatus.CONFLICT, Set.of(SALES));
    }

    @Test
    void duplicateActivePoliciesAreRejected() {
        prepareRequester();
        preparePolicies(Set.of(SALES),
                policy(1L, SALES, "GENERAL"),
                policy(1L, SALES, "SENSITIVE"));

        assertRejected(HttpStatus.CONFLICT, Set.of(SALES));
    }

    @Test
    void emptyTargetsAreRejectedBeforeDatabaseAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> service.validate(USER_ID, Set.of()));

        verifyNoInteractions(policyMapper, userAccountMapper);
    }

    private void prepareRequester() {
        when(policyMapper.selectRequester(USER_ID))
                .thenReturn(requester());
    }

    private UserAccount requester() {
        UserAccount requester = new UserAccount();
        requester.setUserId(USER_ID);
        requester.setDeptId(DEPT_ID);
        requester.setEnabled("Y");
        return requester;
    }

    private void preparePolicies(
            Set<QueryObjectName> targets,
            DataAccessPolicy... policies) {

        when(policyMapper.selectPolicies(DEPT_ID, targets))
                .thenReturn(List.of(policies));
    }

    private DataAccessPolicy policy(
            Long resourceId,
            QueryObjectName target,
            String accessLevel) {

        DataAccessPolicy policy = new DataAccessPolicy();

        policy.setResourceId(resourceId);
        policy.setPolicyId(resourceId + 100L);
        policy.setOwner(target.owner());
        policy.setObjectName(target.objectName());

        // 기존 테스트의 조회 허용 조건을 명시한다.
        policy.setAccessDecision("ALLOW");
        policy.setAccessLevel(accessLevel);

        return policy;
    }

    private void assertRejected(
            HttpStatus expectedStatus,
            Set<QueryObjectName> targets) {

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(USER_ID, targets));

        assertEquals(expectedStatus, error.getStatusCode());
    }
}
