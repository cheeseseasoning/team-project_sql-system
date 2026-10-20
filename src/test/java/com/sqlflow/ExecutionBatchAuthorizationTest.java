package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.ExecutionBatchService;

@ExtendWith(MockitoExtension.class)
class ExecutionBatchAuthorizationTest {

    private static final Long REQUEST_ID = 101L;
    private static final Long USER_ID = 21L;
    private static final Long BATCH_ID = 601L;

    @Mock
    private ExecutionBatchMapper executionBatchMapper;

    @Mock
    private QueryExecutionMapper queryExecutionMapper;

    @InjectMocks
    private ExecutionBatchService executionBatchService;

    @Test
    void rejectsMissingLoginBeforeReadingRequest() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(REQUEST_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(executionBatchMapper, queryExecutionMapper);
    }

    @Test
    void rejectsDisabledDbApprover() {
        SqlFlowUserDetails user = user(false, "DB_APPROVER");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(REQUEST_ID, user));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(executionBatchMapper, queryExecutionMapper);
    }

    @Test
    void rejectsUserWithoutDbApproverRole() {
        SqlFlowUserDetails user = user(true, "DB_ADMIN");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(REQUEST_ID, user));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(executionBatchMapper, queryExecutionMapper);
    }

    @Test
    void rejectsRequestThatIsNotApprovedAndReady() {
        SqlFlowUserDetails user = user(true, "DB_APPROVER");
        ExecutionRequestContext request = requestContext("PENDING", "PENDING_APPROVAL");
        when(executionBatchMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(REQUEST_ID, user));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(executionBatchMapper, never()).insertBatch(any(ExecutionBatch.class));
        verifyNoInteractions(queryExecutionMapper);
    }

    @Test
    void activeDbApproverCreatesBatchAndSqlExecutionRows() {
        SqlFlowUserDetails user = user(true, "DB_APPROVER");

        when(executionBatchMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(requestContext("APPROVED", "READY"));
        when(executionBatchMapper.selectQueryIdsByRequestId(REQUEST_ID))
                .thenReturn(List.of(501L, 502L));
        when(executionBatchMapper.countBatchesByRequestId(REQUEST_ID))
                .thenReturn(0);
        when(executionBatchMapper.insertBatch(any(ExecutionBatch.class)))
                .thenAnswer(invocation -> {
                    ExecutionBatch batch = invocation.getArgument(0);
                    batch.setBatchId(BATCH_ID);
                    return 1;
                });
        when(queryExecutionMapper.insertExecution(any(QueryExecution.class)))
                .thenReturn(1);

        Long result = executionBatchService.createInitialBatch(REQUEST_ID, user);

        assertEquals(BATCH_ID, result);

        ArgumentCaptor<ExecutionBatch> batchCaptor =
                ArgumentCaptor.forClass(ExecutionBatch.class);
        verify(executionBatchMapper).insertBatch(batchCaptor.capture());

        ExecutionBatch batch = batchCaptor.getValue();
        assertEquals(REQUEST_ID, batch.getRequestId());
        assertEquals(USER_ID, batch.getInitiatedBy());
        assertEquals("MANUAL", batch.getTriggerType());
        assertEquals("INITIAL", batch.getBatchType());
        assertEquals("PENDING", batch.getBatchStatus());

        ArgumentCaptor<QueryExecution> executionCaptor =
                ArgumentCaptor.forClass(QueryExecution.class);
        verify(queryExecutionMapper,
                org.mockito.Mockito.times(2)).insertExecution(executionCaptor.capture());

        List<QueryExecution> executions = executionCaptor.getAllValues();
        assertEquals(List.of(501L, 502L),
                executions.stream().map(QueryExecution::getQueryId).toList());

        for (QueryExecution execution : executions) {
            assertEquals(BATCH_ID, execution.getBatchId());
            assertEquals(1, execution.getAttemptNo());
            assertEquals(null, execution.getRetryOfExecutionId());
        }
    }

    private ExecutionRequestContext requestContext(
            String approvalStatus,
            String requestStatus) {
        ExecutionRequestContext context = new ExecutionRequestContext();
        context.setRequestId(REQUEST_ID);
        context.setApprovalStatus(approvalStatus);
        context.setRequestStatus(requestStatus);
        return context;
    }

    private SqlFlowUserDetails user(boolean enabled, String... roleCodes) {
        UserAccount account = new UserAccount();
        account.setUserId(USER_ID);
        account.setLoginId("test-db-user");
        account.setPasswordHash("{bcrypt}test");
        account.setUserName("DB 테스트 사용자");
        account.setDeptId(2L);
        account.setDeptName("DB팀");
        account.setPositionName("대리");
        account.setEnabled(enabled ? "Y" : "N");

        return new SqlFlowUserDetails(account, List.of(roleCodes));
    }
}