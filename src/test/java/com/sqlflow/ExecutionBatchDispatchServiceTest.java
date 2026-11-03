package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchDispatchService;
import com.sqlflow.execution.service.ExecutionBatchRecordService;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchStartResult;
import com.sqlflow.execution.service.ExecutionBatchRunService;

/**
 * 실행 접수와 백그라운드 작업 전달을 확인한다.
 * 실제 스레드 대신 전달된 작업을 직접 실행해 결과를 일정하게 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionBatchDispatchServiceTest {

    private static final Long BATCH_ID = 100L;
    private static final Long ACTOR_ID = 10L;
    private static final Long REQUEST_ID = 200L;

    @Mock
    private ExecutionBatchRecordService recordService;

    @Mock
    private ExecutionBatchRunService runService;

    @Mock
    private Executor executor;


    @Mock
    private SqlFlowUserDetails user;

    private ExecutionBatchDispatchService service;
    private BatchStartResult started;

    @BeforeEach
    void setUp() {
        service = new ExecutionBatchDispatchService(
                recordService, runService, executor);

        started = new BatchStartResult(
                BATCH_ID, 200L, List.of(501L, 502L));
    }

    @Test
    void submitsTaskWithoutWaitingForSqlExecution() {
        prepareStart();

        service.submit(BATCH_ID, user);

        var taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());

        // 접수 메소드가 반환될 때 실제 SQL 실행은 아직 호출하지 않았다.
        verifyNoInteractions(runService);

        taskCaptor.getValue().run();

        verify(runService).executeStarted(started);
        verify(recordService, never()).cancelRejectedStart(anyLong());
    }

    @Test
    void rejectedTaskRestoresStateAndReturns503() {
        prepareStart();

        doThrow(new RejectedExecutionException("실행 중"))
                .when(executor).execute(any(Runnable.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(BATCH_ID, user));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        verify(recordService).cancelRejectedStart(BATCH_ID);
        verifyNoInteractions(runService);
    }

    @Test
    void restoreFailureReturns500() {
        prepareStart();

        var rejection = new RejectedExecutionException("접수 거절");
        var restoreFailure = new IllegalStateException("Oracle 복원 실패");

        doThrow(rejection)
                .when(executor).execute(any(Runnable.class));

        doThrow(restoreFailure)
                .when(recordService).cancelRejectedStart(BATCH_ID);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(BATCH_ID, user));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatusCode());
        assertSame(rejection, exception.getCause());
        assertArrayEquals(
                new Throwable[] { restoreFailure },
                rejection.getSuppressed());

        verifyNoInteractions(runService);
    }

    @Test
    void startFailureDoesNotSubmitTaskOrRestoreAnotherExecution() {
        when(user.getUserId()).thenReturn(ACTOR_ID);

        var failure = new ResponseStatusException(
                HttpStatus.CONFLICT, "이미 시작된 묶음");

        when(recordService.start(BATCH_ID, ACTOR_ID)).thenThrow(failure);

        assertSame(failure, assertThrows(
                ResponseStatusException.class,
                () -> service.submit(BATCH_ID, user)));

        verifyNoInteractions(executor, runService);
        verify(recordService, never()).cancelRejectedStart(anyLong());
    }

    @Test
    void unauthenticatedUserCannotSubmit() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.submit(BATCH_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(recordService, executor, runService);
    }

    @Test
    void workerFailureDoesNotRestoreAlreadyStartedBatch() {
        prepareStart();

        when(runService.executeStarted(started))
                .thenThrow(new IllegalStateException("결과 저장 상태 확인 필요"));

        service.submit(BATCH_ID, user);

        var taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());

        // 실행 중 장애는 로그를 남기며, 접수 거절처럼 대기 상태로 되돌리지 않는다.
        assertDoesNotThrow(() -> taskCaptor.getValue().run());

        verify(runService).executeStarted(started);
        verify(recordService, never()).cancelRejectedStart(anyLong());
    }
    
    @Test
    void 전체실행은_미실행배치를_예약하고_기존_순차실행에_전달한다() {
        prepareRemainingStart();

        var accepted = service.submitRemaining(REQUEST_ID, user);

        assertEquals(started, accepted);

        var taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());

        // 아직 백그라운드 작업을 실행하지 않은 상태다.
        verifyNoInteractions(runService);

        taskCaptor.getValue().run();

        verify(recordService).reserveRemaining(REQUEST_ID, ACTOR_ID);
        verify(runService).executeStarted(started);

        // 기존 배치 시작 메서드를 다시 호출하면 안 된다.
        verify(recordService, never()).start(anyLong(), anyLong());
        verify(recordService, never())
                .cancelRejectedRemainingStart(any());
    }

    @Test
    void 자동배치_접수가_거절되면_새_복구메서드를_호출한다() {
        prepareRemainingStart();

        doThrow(new RejectedExecutionException("실행기 사용 중"))
                .when(executor).execute(any(Runnable.class));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.submitRemaining(REQUEST_ID, user));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        verify(recordService).cancelRejectedRemainingStart(started);

        // 기존 배치 복구와 혼동하지 않도록 확인한다.
        verify(recordService, never()).cancelRejectedStart(anyLong());
        verifyNoInteractions(runService);
    }

    @Test
    void 자동배치_복구도_실패하면_500을_반환한다() {
        prepareRemainingStart();

        var rejection = new RejectedExecutionException("접수 거절");
        var restoration = new IllegalStateException("복구 실패");

        doThrow(rejection)
                .when(executor).execute(any(Runnable.class));

        doThrow(restoration)
                .when(recordService)
                .cancelRejectedRemainingStart(started);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.submitRemaining(REQUEST_ID, user));

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                error.getStatusCode());

        assertSame(rejection, error.getCause());
        assertArrayEquals(
                new Throwable[] {restoration},
                rejection.getSuppressed());

        verifyNoInteractions(runService);
    }

    @Test
    void 전체실행_예약이_실패하면_실행기에_전달하지_않는다() {
        when(user.getUserId()).thenReturn(ACTOR_ID);

        var failure = new ResponseStatusException(
                HttpStatus.CONFLICT,
                "실행할 SQL이 없습니다.");

        when(recordService.reserveRemaining(REQUEST_ID, ACTOR_ID))
                .thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.submitRemaining(REQUEST_ID, user)));

        verifyNoInteractions(executor, runService);

        verify(recordService, never())
                .cancelRejectedRemainingStart(any());
    }

    @Test
    void 전체실행중_장애는_접수거절_복구를_호출하지_않는다() {
        prepareRemainingStart();

        when(runService.executeStarted(started))
                .thenThrow(new IllegalStateException(
                        "결과 저장 상태 확인 필요"));

        service.submitRemaining(REQUEST_ID, user);

        var taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(executor).execute(taskCaptor.capture());

        assertDoesNotThrow(() -> taskCaptor.getValue().run());

        verify(recordService, never())
                .cancelRejectedRemainingStart(any());
    }

    @Test
    void 로그인하지_않으면_전체실행을_접수하지_않는다() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.submitRemaining(REQUEST_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(recordService, executor, runService);
    }

    private void prepareRemainingStart() {
        when(user.getUserId()).thenReturn(ACTOR_ID);

        when(recordService.reserveRemaining(REQUEST_ID, ACTOR_ID))
                .thenReturn(started);
    }

    private void prepareStart() {
        when(user.getUserId()).thenReturn(ACTOR_ID);
        when(recordService.start(BATCH_ID, ACTOR_ID)).thenReturn(started);
    }
}
