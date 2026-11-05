package com.sqlflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.ExecutionBatchRecordService;
import com.sqlflow.execution.service.RequestExecutionStatusService;

@ExtendWith(MockitoExtension.class)
class RemainingBatchReservationTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long ACTOR_ID = 300L;
    private static final Long BATCH_ID = 500L;

    @Mock
    private ExecutionBatchMapper batchMapper;

    @Mock
    private QueryExecutionMapper queryExecutionMapper;
    
    @Mock
    private RequestExecutionStatusService requestStatusService;

    @InjectMocks
    private ExecutionBatchRecordService service;

    @ParameterizedTest
    @ValueSource(strings = {"READY", "RUNNING"})
    void 미실행_SQL만_요청순서대로_배치에_등록한다(
            String requestStatus) {
        prepareRequest(requestStatus, "APPROVED");

        when(queryExecutionMapper.selectLatestExecutionsByRequestId(
                REQUEST_ID))
                .thenReturn(List.of(
                        item(501L, 901L, "SUCCESS"),
                        item(502L, 902L, "FAILED"),
                        item(504L, null, null),
                        item(503L, null, null)));

        when(batchMapper.insertBatch(any(ExecutionBatch.class)))
                .thenAnswer(invocation -> {
                    ExecutionBatch batch = invocation.getArgument(0);
                    batch.setBatchId(BATCH_ID);
                    return 1;
                });

        AtomicLong sequence = new AtomicLong(1000L);

        when(queryExecutionMapper.insertExecution(any(QueryExecution.class)))
                .thenAnswer(invocation -> {
                    QueryExecution execution = invocation.getArgument(0);
                    execution.setExecutionId(sequence.incrementAndGet());
                    return 1;
                });

        when(batchMapper.markBatchRunning(BATCH_ID, ACTOR_ID))
                .thenReturn(1);

        if ("READY".equals(requestStatus)) {
            when(batchMapper.updateRequestStatus(
                    REQUEST_ID, "READY", "RUNNING")).thenReturn(1);
        }

        var started =
                service.reserveRemaining(REQUEST_ID, ACTOR_ID);

        assertEquals(BATCH_ID, started.batchId());
        assertEquals(REQUEST_ID, started.requestId());
        assertEquals(
                List.of(1001L, 1002L),
                started.executionIds());

        ArgumentCaptor<ExecutionBatch> batchCaptor =
                ArgumentCaptor.forClass(ExecutionBatch.class);

        verify(batchMapper).insertBatch(batchCaptor.capture());

        ExecutionBatch batch = batchCaptor.getValue();
        assertEquals(REQUEST_ID, batch.getRequestId());
        assertEquals(ACTOR_ID, batch.getInitiatedBy());
        assertEquals("INITIAL", batch.getBatchType());
        assertEquals("MANUAL", batch.getTriggerType());
        assertEquals("PENDING", batch.getBatchStatus());

        ArgumentCaptor<QueryExecution> executionCaptor =
                ArgumentCaptor.forClass(QueryExecution.class);

        verify(queryExecutionMapper, times(2))
                .insertExecution(executionCaptor.capture());

        var executions = executionCaptor.getAllValues();

        // SQL ID의 크기가 아니라 매퍼가 반환한 요청 순서를 유지한다.
        assertEquals(
                List.of(504L, 503L),
                executions.stream()
                        .map(QueryExecution::getQueryId)
                        .toList());

        for (var execution : executions) {
            assertEquals(BATCH_ID, execution.getBatchId());
            assertEquals(ACTOR_ID, execution.getExecutedBy());
            assertEquals(1, execution.getAttemptNo());
            assertNull(execution.getRetryOfExecutionId());
        }

        var order = inOrder(batchMapper, queryExecutionMapper);
        order.verify(batchMapper)
                .selectRequestContextForUpdate(REQUEST_ID);
        order.verify(queryExecutionMapper)
                .countActiveExecutionsByRequestId(REQUEST_ID);
        order.verify(queryExecutionMapper)
                .selectLatestExecutionsByRequestId(REQUEST_ID);
        order.verify(batchMapper)
                .insertBatch(any(ExecutionBatch.class));

        if ("READY".equals(requestStatus)) {
            verify(batchMapper).updateRequestStatus(
                    REQUEST_ID, "READY", "RUNNING");
        } else {
            verify(batchMapper, never()).updateRequestStatus(
                    anyLong(), anyString(), anyString());
        }
    }

    @Test
    void 권한이_없으면_예약하지_않는다() {
        when(batchMapper.isActiveDbApprover(ACTOR_ID))
                .thenReturn(false);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserveRemaining(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(queryExecutionMapper);
        verify(batchMapper, never())
                .selectRequestContextForUpdate(anyLong());
    }

    @Test
    void 최종_승인되지_않으면_예약하지_않는다() {
        prepareRequest("READY", "PENDING");

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserveRemaining(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(queryExecutionMapper);
    }

    @Test
    void 다른_SQL이_대기나_실행중이면_예약하지_않는다() {
        prepareRequest("RUNNING", "APPROVED");

        when(queryExecutionMapper.countActiveExecutionsByRequestId(
                REQUEST_ID)).thenReturn(1);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserveRemaining(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(batchMapper, never()).insertBatch(any());
    }

    @Test
    void 모두_실행한_SQL이면_새_배치를_만들지_않는다() {
        prepareRequest("RUNNING", "APPROVED");

        when(queryExecutionMapper.selectLatestExecutionsByRequestId(
                REQUEST_ID))
                .thenReturn(List.of(
                        item(501L, 901L, "SUCCESS"),
                        item(502L, 902L, "FAILED")));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserveRemaining(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(batchMapper, never()).insertBatch(any());
    }

    @Test
    void 기존_배치가_있으면_최초_배치를_중복_생성하지_않는다() {
        prepareRequest("RUNNING", "APPROVED");

        when(queryExecutionMapper.selectLatestExecutionsByRequestId(
                REQUEST_ID))
                .thenReturn(List.of(item(501L, null, null)));

        when(batchMapper.countBatchesByRequestId(REQUEST_ID))
                .thenReturn(1);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserveRemaining(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(batchMapper, never()).insertBatch(any());
    }

    private void prepareRequest(
            String requestStatus,
            String approvalStatus) {
        when(batchMapper.isActiveDbApprover(ACTOR_ID))
                .thenReturn(true);

        ExecutionRequestContext request =
                new ExecutionRequestContext();

        request.setRequestId(REQUEST_ID);
        request.setRequestStatus(requestStatus);
        request.setApprovalStatus(approvalStatus);

        when(batchMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);
    }

    private QueryExecution item(
            Long queryId,
            Long executionId,
            String status) {
        QueryExecution execution = new QueryExecution();
        execution.setQueryId(queryId);
        execution.setExecutionId(executionId);
        execution.setStatus(status);
        return execution;
    }
}