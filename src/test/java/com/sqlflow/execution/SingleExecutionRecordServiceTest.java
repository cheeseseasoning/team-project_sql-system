package com.sqlflow.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

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

import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.SingleExecutionRecordService;

@ExtendWith(MockitoExtension.class)
class SingleExecutionRecordServiceTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long QUERY_ID = 200L;
    private static final Long ACTOR_ID = 300L;
    private static final Long EXECUTION_ID = 400L;

    @Mock
    private ExecutionBatchMapper requestMapper;

    @Mock
    private QueryExecutionMapper executionMapper;

    @InjectMocks
    private SingleExecutionRecordService service;

    @ParameterizedTest
    @ValueSource(strings = {"READY", "RUNNING"})
    void 배치_없이_실행자를_저장하고_개별_실행을_예약한다(
            String requestStatus) {
        prepareValidTarget(requestStatus);

        when(executionMapper.insertExecution(any(QueryExecution.class)))
                .thenAnswer(invocation -> {
                    QueryExecution execution = invocation.getArgument(0);
                    execution.setExecutionId(EXECUTION_ID);
                    return 1;
                });

        when(executionMapper.markRunning(EXECUTION_ID)).thenReturn(1);

        if ("READY".equals(requestStatus)) {
            when(requestMapper.updateRequestStatus(
                    REQUEST_ID, "READY", "RUNNING")).thenReturn(1);
        }

        var result = service.reserve(REQUEST_ID, QUERY_ID, ACTOR_ID);

        assertEquals(REQUEST_ID, result.requestId());
        assertEquals(QUERY_ID, result.queryId());
        assertEquals(EXECUTION_ID, result.executionId());

        ArgumentCaptor<QueryExecution> captor =
                ArgumentCaptor.forClass(QueryExecution.class);

        verify(executionMapper).insertExecution(captor.capture());

        QueryExecution saved = captor.getValue();
        assertNull(saved.getBatchId());
        assertEquals(QUERY_ID, saved.getQueryId());
        assertEquals(ACTOR_ID, saved.getExecutedBy());
        assertEquals(1, saved.getAttemptNo());
        assertNull(saved.getRetryOfExecutionId());

        // 잠금 후 중복 검사와 이력 생성이 수행되는지 확인한다.
        var order = inOrder(requestMapper, executionMapper);
        order.verify(requestMapper)
                .selectRequestContextForUpdate(REQUEST_ID);
        order.verify(executionMapper)
                .countExecutionsByQueryId(QUERY_ID);
        order.verify(executionMapper)
                .countActiveExecutionsByRequestId(REQUEST_ID);
        order.verify(executionMapper)
                .insertExecution(any(QueryExecution.class));
        order.verify(executionMapper).markRunning(EXECUTION_ID);

        if ("READY".equals(requestStatus)) {
            verify(requestMapper).updateRequestStatus(
                    REQUEST_ID, "READY", "RUNNING");
        } else {
            verify(requestMapper, never()).updateRequestStatus(
                    anyLong(), anyString(), anyString());
        }

        verify(requestMapper, never())
                .insertBatch(any());
    }

    @Test
    void 로그인하지_않으면_접수하지_않는다() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(REQUEST_ID, QUERY_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(requestMapper, executionMapper);
    }

    @Test
    void 실행_권한이_없으면_접수하지_않는다() {
        when(requestMapper.isActiveDbApprover(ACTOR_ID))
                .thenReturn(false);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(
                        REQUEST_ID, QUERY_ID, ACTOR_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verifyNoInteractions(executionMapper);
        verify(requestMapper, never())
                .selectRequestContextForUpdate(anyLong());
    }

    @Test
    void 최종_승인되지_않은_기안서는_접수하지_않는다() {
        prepareRequest("READY", "PENDING");

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(
                        REQUEST_ID, QUERY_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(executionMapper);
    }

    @Test
    void 다른_기안서의_SQL은_접수하지_않는다() {
        prepareValidTarget("READY");

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(
                        REQUEST_ID, 999L, ACTOR_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(executionMapper);
    }

    @Test
    void 기존_실행_이력이_있으면_중복_접수하지_않는다() {
        prepareValidTarget("READY");

        when(executionMapper.countExecutionsByQueryId(QUERY_ID))
                .thenReturn(1);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(
                        REQUEST_ID, QUERY_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(executionMapper, never()).insertExecution(any());
    }

    @Test
    void 같은_기안서에_대기나_실행중_SQL이_있으면_접수하지_않는다() {
        prepareValidTarget("RUNNING");

        when(executionMapper.countActiveExecutionsByRequestId(REQUEST_ID))
                .thenReturn(1);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.reserve(
                        REQUEST_ID, QUERY_ID, ACTOR_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(executionMapper, never()).insertExecution(any());
    }
    
    @Test
    void 접수가_거절되고_다른_이력이_없으면_READY로_복원한다() {
        prepareCancellationRequest();

        var started = new SingleExecutionRecordService.SingleStartResult(
                REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(executionMapper.deleteRejectedStandaloneReservation(
                REQUEST_ID, QUERY_ID, EXECUTION_ID)).thenReturn(1);

        when(executionMapper.countExecutionsByRequestId(REQUEST_ID))
                .thenReturn(0);

        when(requestMapper.updateRequestStatus(
                REQUEST_ID, "RUNNING", "READY")).thenReturn(1);

        service.cancelRejectedStart(started);

        var order = inOrder(requestMapper, executionMapper);

        order.verify(requestMapper)
                .selectRequestContextForUpdate(REQUEST_ID);
        order.verify(executionMapper)
                .deleteRejectedStandaloneReservation(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID);
        order.verify(executionMapper)
                .countExecutionsByRequestId(REQUEST_ID);
        order.verify(requestMapper)
                .updateRequestStatus(
                        REQUEST_ID, "RUNNING", "READY");
    }

    @Test
    void 이전_실행_이력이_있으면_예약만_제거하고_요청상태는_유지한다() {
        prepareCancellationRequest();

        var started = new SingleExecutionRecordService.SingleStartResult(
                REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(executionMapper.deleteRejectedStandaloneReservation(
                REQUEST_ID, QUERY_ID, EXECUTION_ID)).thenReturn(1);

        when(executionMapper.countExecutionsByRequestId(REQUEST_ID))
                .thenReturn(1);

        service.cancelRejectedStart(started);

        verify(executionMapper).deleteRejectedStandaloneReservation(
                REQUEST_ID, QUERY_ID, EXECUTION_ID);

        verify(requestMapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    @Test
    void 예약을_제거하지_못하면_요청상태를_바꾸지_않는다() {
        prepareCancellationRequest();

        var started = new SingleExecutionRecordService.SingleStartResult(
                REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(executionMapper.deleteRejectedStandaloneReservation(
                REQUEST_ID, QUERY_ID, EXECUTION_ID)).thenReturn(0);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.cancelRejectedStart(started));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(executionMapper, never())
                .countExecutionsByRequestId(anyLong());

        verify(requestMapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    private void prepareCancellationRequest() {
        ExecutionRequestContext request = new ExecutionRequestContext();
        request.setRequestId(REQUEST_ID);
        request.setRequestStatus("RUNNING");
        request.setApprovalStatus("APPROVED");

        when(requestMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);
    }

    private void prepareValidTarget(String requestStatus) {
        prepareRequest(requestStatus, "APPROVED");

        when(requestMapper.selectQueryIdsByRequestId(REQUEST_ID))
                .thenReturn(List.of(QUERY_ID));
    }

    private void prepareRequest(
            String requestStatus,
            String approvalStatus) {
        when(requestMapper.isActiveDbApprover(ACTOR_ID))
                .thenReturn(true);

        ExecutionRequestContext request =
                new ExecutionRequestContext();

        request.setRequestId(REQUEST_ID);
        request.setRequestStatus(requestStatus);
        request.setApprovalStatus(approvalStatus);

        when(requestMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);
    }
}