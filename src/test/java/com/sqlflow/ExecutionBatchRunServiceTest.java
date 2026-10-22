package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchRecordService;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchExecutionResult;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchStartResult;
import com.sqlflow.execution.service.ExecutionBatchRunService;
import com.sqlflow.execution.service.QueryExecutionService;
import com.sqlflow.execution.service.QueryExecutionService.ExecutionOutcome;

/** 실행 순서와 일반 실패·인프라 장애의 처리 차이를 확인한다. */
@ExtendWith(MockitoExtension.class)
class ExecutionBatchRunServiceTest {

    private static final Long BATCH_ID = 100L;
    private static final Long REQUEST_ID = 200L;
    private static final Long ACTOR_ID = 10L;

    @Mock
    private ExecutionBatchRecordService recordService;

    @Mock
    private QueryExecutionService queryService;

    @Mock
    private SqlFlowUserDetails user;

    @InjectMocks
    private ExecutionBatchRunService service;

    private BatchStartResult started;

    @BeforeEach
    void setUp() {
        started = new BatchStartResult(
                BATCH_ID, REQUEST_ID, List.of(501L, 502L, 503L));
    }

    @Test
    void continuesAfterSqlFailureAndFinishesInOrder() {
        prepareStart();

        when(queryService.execute(501L))
                .thenReturn(new ExecutionOutcome(501L, true));
        when(queryService.execute(502L))
                .thenReturn(new ExecutionOutcome(502L, false));
        when(queryService.execute(503L))
                .thenReturn(new ExecutionOutcome(503L, true));

        BatchExecutionResult expected = new BatchExecutionResult(
                BATCH_ID, REQUEST_ID,
                "PARTIAL_FAILED", "PARTIAL_FAILED", 2, 1);

        when(recordService.finish(BATCH_ID)).thenReturn(expected);

        var result = service.execute(BATCH_ID, user);

        assertSame(expected, result);

        var order = inOrder(recordService, queryService);
        order.verify(recordService).start(BATCH_ID, ACTOR_ID);
        order.verify(queryService).execute(501L);
        order.verify(queryService).execute(502L);
        order.verify(queryService).execute(503L);
        order.verify(recordService).finish(BATCH_ID);
    }

    @Test
    void infrastructureFailureStopsRemainingSqlAndFinalization() {
        prepareStart();

        when(queryService.execute(501L))
                .thenReturn(new ExecutionOutcome(501L, true));

        var failure = new IllegalStateException("결과 저장 상태 확인 필요");
        when(queryService.execute(502L)).thenThrow(failure);

        assertSame(failure, assertThrows(
                IllegalStateException.class,
                () -> service.execute(BATCH_ID, user)));

        verify(queryService, never()).execute(503L);
        verify(recordService, never()).finish(anyLong());
    }

    @Test
    void unauthenticatedUserCannotStartBatch() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.execute(BATCH_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(recordService, queryService);
    }

    private void prepareStart() {
        when(user.getUserId()).thenReturn(ACTOR_ID);
        when(recordService.start(BATCH_ID, ACTOR_ID)).thenReturn(started);
    }
}