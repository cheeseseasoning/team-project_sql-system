package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.ExecutionBatchService;

@ExtendWith(MockitoExtension.class)
class ExecutionBatchAuthorizationTest {

    private static final Long REQUEST_ID = 101L;
    private static final Long USER_ID = 21L;

    @Mock
    private ExecutionBatchMapper executionBatchMapper;

    @Mock
    private QueryExecutionMapper queryExecutionMapper;

    @InjectMocks
    private ExecutionBatchService executionBatchService;

    @Test
    void rejectsUserWhoIsNotAnActiveDbTeamMemberBeforeReadingRequest() {
        when(executionBatchMapper.isActiveDbTeamMember(USER_ID)).thenReturn(false);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(REQUEST_ID, USER_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(executionBatchMapper, never()).selectApprovalStatusForUpdate(REQUEST_ID);
        verify(executionBatchMapper, never()).insertBatch(any(ExecutionBatch.class));
    }

    @Test
    void allowsActiveDbTeamMemberToCreateInitialExecutionBatch() {
        when(executionBatchMapper.isActiveDbTeamMember(USER_ID)).thenReturn(true);
        when(executionBatchMapper.selectApprovalStatusForUpdate(REQUEST_ID))
                .thenReturn("APPROVED");
        when(executionBatchMapper.selectQueryIdsByRequestId(REQUEST_ID))
                .thenReturn(List.of(501L));
        when(executionBatchMapper.countBatchesByRequestId(REQUEST_ID)).thenReturn(0);
        when(executionBatchMapper.insertBatch(any(ExecutionBatch.class)))
                .thenAnswer(invocation -> {
                    ((ExecutionBatch) invocation.getArgument(0)).setBatchId(601L);
                    return 1;
                });
        when(queryExecutionMapper.insertExecution(any(QueryExecution.class))).thenReturn(1);

        Long batchId = executionBatchService.createInitialBatch(REQUEST_ID, USER_ID);

        assertEquals(601L, batchId);
        verify(queryExecutionMapper).insertExecution(any(QueryExecution.class));
    }
}
