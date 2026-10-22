package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.service.ExecutionBatchRecordService;

/**
 * 실행 묶음의 시작 조건과 SQL별 결과 집계를 확인한다.
 * 실제 Oracle 잠금·롤백은 별도 연동 테스트에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionBatchRecordServiceTest {

    private static final Long BATCH_ID = 100L;
    private static final Long REQUEST_ID = 200L;
    private static final Long ACTOR_ID = 10L;

    @Mock
    private ExecutionBatchMapper mapper;

    @InjectMocks
    private ExecutionBatchRecordService service;

    private ExecutionBatch batch;
    private ExecutionRequestContext request;

    @BeforeEach
    void setUp() {
        batch = new ExecutionBatch();
        batch.setBatchId(BATCH_ID);
        batch.setRequestId(REQUEST_ID);
        batch.setBatchType("INITIAL");
        batch.setBatchStatus("PENDING");

        request = new ExecutionRequestContext();
        request.setRequestId(REQUEST_ID);
        request.setApprovalStatus("APPROVED");
        request.setRequestStatus("READY");
    }

    @Test
    void startsBatchAndPreservesExecutionOrder() {
        prepareStart();

        when(mapper.markBatchRunning(BATCH_ID, ACTOR_ID)).thenReturn(1);
        when(mapper.updateRequestStatus(
                REQUEST_ID, "READY", "RUNNING")).thenReturn(1);

        var result = service.start(BATCH_ID, ACTOR_ID);

        assertEquals(BATCH_ID, result.batchId());
        assertEquals(REQUEST_ID, result.requestId());

        // Mapper가 반환한 등록 순서를 유지해야 한다.
        assertEquals(List.of(501L, 502L), result.executionIds());

        var order = inOrder(mapper);
        order.verify(mapper).isActiveDbApprover(ACTOR_ID);
        order.verify(mapper).selectBatch(BATCH_ID);
        order.verify(mapper).selectRequestContextForUpdate(REQUEST_ID);
        order.verify(mapper).selectExecutions(BATCH_ID);
        order.verify(mapper).selectQueryIdsByRequestId(REQUEST_ID);
        order.verify(mapper).markBatchRunning(BATCH_ID, ACTOR_ID);
        order.verify(mapper).updateRequestStatus(
                REQUEST_ID, "READY", "RUNNING");
    }

    @Test
    void rejectsUserWithoutCurrentExecutionPermission() {
        when(mapper.isActiveDbApprover(ACTOR_ID)).thenReturn(false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start(BATCH_ID, ACTOR_ID));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(mapper, never()).selectBatch(anyLong());
        verify(mapper, never()).markBatchRunning(anyLong(), anyLong());
    }

    @Test
    void duplicateStartDoesNotChangeRequestStatus() {
        prepareStart();
        when(mapper.markBatchRunning(BATCH_ID, ACTOR_ID)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.start(BATCH_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());

        verify(mapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, SUCCESS, SUCCESS, COMPLETED, 2, 0",
            "SUCCESS, FAILED, PARTIAL_FAILED, PARTIAL_FAILED, 1, 1",
            "FAILED, FAILED, FAILED, FAILED, 0, 2"
    })
    void aggregatesFinalStatuses(
            String firstStatus,
            String secondStatus,
            String expectedBatchStatus,
            String expectedRequestStatus,
            int expectedSuccessCount,
            int expectedFailureCount) {

        prepareFinish(firstStatus, secondStatus);

        when(mapper.updateStatus(
                BATCH_ID, "RUNNING", expectedBatchStatus)).thenReturn(1);
        when(mapper.updateRequestStatus(
                REQUEST_ID, "RUNNING", expectedRequestStatus)).thenReturn(1);

        var result = service.finish(BATCH_ID);

        assertEquals(expectedBatchStatus, result.batchStatus());
        assertEquals(expectedRequestStatus, result.requestStatus());
        assertEquals(expectedSuccessCount, result.successCount());
        assertEquals(expectedFailureCount, result.failureCount());

        verify(mapper).updateStatus(
                BATCH_ID, "RUNNING", expectedBatchStatus);
        verify(mapper).updateRequestStatus(
                REQUEST_ID, "RUNNING", expectedRequestStatus);
    }

    @Test
    void unfinishedSqlPreventsBatchCompletion() {
        prepareFinish("SUCCESS", "PENDING");

        assertThrows(
                ResponseStatusException.class,
                () -> service.finish(BATCH_ID));

        verify(mapper, never()).updateStatus(
                anyLong(), anyString(), anyString());
        verify(mapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    @Test
    void requestUpdateFailureIsReported() {
        prepareFinish("SUCCESS", "SUCCESS");

        when(mapper.updateStatus(
                BATCH_ID, "RUNNING", "SUCCESS")).thenReturn(1);
        when(mapper.updateRequestStatus(
                REQUEST_ID, "RUNNING", "COMPLETED")).thenReturn(0);

        assertThrows(
                ResponseStatusException.class,
                () -> service.finish(BATCH_ID));

        // 이 테스트는 예외 전달만 확인한다.
        // 묶음 SUCCESS 변경의 실제 롤백은 Oracle 연동 테스트에서 확인한다.
        verify(mapper).updateStatus(BATCH_ID, "RUNNING", "SUCCESS");
    }

    private void prepareStart() {
        when(mapper.isActiveDbApprover(ACTOR_ID)).thenReturn(true);
        when(mapper.selectBatch(BATCH_ID)).thenReturn(batch);
        when(mapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);

        when(mapper.selectExecutions(BATCH_ID)).thenReturn(List.of(
                execution(501L, 301L, "PENDING"),
                execution(502L, 302L, "PENDING")));

        when(mapper.selectQueryIdsByRequestId(REQUEST_ID))
                .thenReturn(List.of(301L, 302L));
    }

    private void prepareFinish(String firstStatus, String secondStatus) {
        batch.setBatchStatus("RUNNING");
        request.setRequestStatus("RUNNING");

        when(mapper.selectBatch(BATCH_ID)).thenReturn(batch);
        when(mapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);

        when(mapper.selectExecutions(BATCH_ID)).thenReturn(List.of(
                execution(501L, 301L, firstStatus),
                execution(502L, 302L, secondStatus)));
    }

    private QueryExecution execution(
            Long executionId, Long queryId, String status) {

        QueryExecution execution = new QueryExecution();
        execution.setExecutionId(executionId);
        execution.setBatchId(BATCH_ID);
        execution.setQueryId(queryId);
        execution.setStatus(status);

        return execution;
    }
}