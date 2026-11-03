package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.ExecutionBatchRecordService;
import com.sqlflow.execution.service.RequestExecutionStatusService;

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

    @Mock
    private QueryExecutionMapper queryExecutionMapper;
    
    @Mock
    private RequestExecutionStatusService requestStatusService;

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
    void reservesOnePendingSqlWithoutStartingWholeBatch() {
        when(mapper.isActiveDbApprover(ACTOR_ID)).thenReturn(true);

        when(mapper.selectBatch(BATCH_ID)).thenReturn(batch);

        when(mapper.selectRequestContextForUpdate(REQUEST_ID)).thenReturn(request);

        var target = new com.sqlflow.execution.domain.QueryExecutionTarget();

        target.setBatchId(BATCH_ID);
        target.setRequestId(REQUEST_ID);
        target.setExecutionStatus("PENDING");

        when(queryExecutionMapper.selectExecutionTarget(501L)).thenReturn(target);

        when(queryExecutionMapper.markRunning(501L)).thenReturn(1);

        when(mapper.updateRequestStatus(REQUEST_ID, "READY", "RUNNING")).thenReturn(1);

        var result = service.startSingle(BATCH_ID, 501L, ACTOR_ID);

        assertEquals(501L, result.executionId());

        // 선택한 SQL과 요청은 실행 중으로 변경한다.
        verify(queryExecutionMapper).markRunning(501L);

        verify(mapper).updateRequestStatus(
                REQUEST_ID,
                "READY",
                "RUNNING");

        // 개별 실행이므로 전체 묶음은 시작하지 않는다.
        verify(mapper, never()).markBatchRunning(
                anyLong(),
                anyLong());
    }

    @Test
    void singleSqlCompletionWaitsUntilAllSqlAreTerminal() {
        batch.setBatchStatus("PENDING");
        request.setRequestStatus("RUNNING");
        when(mapper.selectBatch(BATCH_ID)).thenReturn(batch);
        when(mapper.selectRequestContextForUpdate(REQUEST_ID)).thenReturn(request);
        when(mapper.selectExecutions(BATCH_ID)).thenReturn(List.of(
                execution(501L, 301L, "SUCCESS"), execution(502L, 302L, "PENDING")));

        assertNull(service.finishIfComplete(BATCH_ID));
        verify(mapper, never()).updateStatus(anyLong(), anyString(), anyString());
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

        var failure = new ResponseStatusException(
                HttpStatus.CONFLICT,
                "기안서 상태 갱신 실패");

        when(requestStatusService.refresh(REQUEST_ID))
                .thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.finish(BATCH_ID)));

        // 실제 롤백은 Oracle 통합 테스트에서 확인한다.
        verify(mapper).updateStatus(
                BATCH_ID, "RUNNING", "SUCCESS");
    }
    
    @Test
    void 배치가_성공해도_기안서는_이전_개별실패를_포함해_집계한다() {
        prepareFinish("SUCCESS", "SUCCESS");

        when(mapper.updateStatus(
                BATCH_ID, "RUNNING", "SUCCESS")).thenReturn(1);

        when(requestStatusService.refresh(REQUEST_ID))
                .thenReturn("PARTIAL_FAILED");

        var result = service.finish(BATCH_ID);

        assertEquals("SUCCESS", result.batchStatus());
        assertEquals("PARTIAL_FAILED", result.requestStatus());
        assertEquals(2, result.successCount());
        assertEquals(0, result.failureCount());
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

        when(requestStatusService.refresh(REQUEST_ID))
                .thenReturn(expectedRequestStatus);

        var result = service.finish(BATCH_ID);

        assertEquals(expectedBatchStatus, result.batchStatus());
        assertEquals(expectedRequestStatus, result.requestStatus());
        assertEquals(expectedSuccessCount, result.successCount());
        assertEquals(expectedFailureCount, result.failureCount());

        var order = inOrder(mapper, requestStatusService);

        order.verify(mapper).updateStatus(
                BATCH_ID, "RUNNING", expectedBatchStatus);
        order.verify(requestStatusService).refresh(REQUEST_ID);

        // 기안서 갱신은 공통 집계 서비스에 위임한다.
        verify(mapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }
    
    private void prepareStart() {
        when(mapper.isActiveDbApprover(ACTOR_ID))
                .thenReturn(true);

        when(mapper.selectBatch(BATCH_ID))
                .thenReturn(batch);

        when(mapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);

        when(mapper.selectExecutions(BATCH_ID))
                .thenReturn(List.of(
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
