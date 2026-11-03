package com.sqlflow.execution.service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchStartResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 실행 요청을 검증·시작 처리한 후 전용 실행 스레드에 전달한다.
 * HTTP 요청 스레드는 실제 SQL 실행이 끝날 때까지 기다리지 않는다.
 */
@Slf4j
@Service
public class ExecutionBatchDispatchService {

    private final ExecutionBatchRecordService recordService;
    private final ExecutionBatchRunService runService;
    private final Executor executor;

    public ExecutionBatchDispatchService(
            ExecutionBatchRecordService recordService,
            ExecutionBatchRunService runService,
            @Qualifier("sqlExecutionExecutor") Executor executor) {

        this.recordService = recordService;
        this.runService = runService;
        this.executor = executor;
    }

    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public void submit(Long batchId, SqlFlowUserDetails user) {

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // 별도 트랜잭션으로 시작 처리를 커밋한 뒤 작업을 전달한다.
        // 같은 묶음의 중복 요청은 여기서 차단된다.
        var started = recordService.start(batchId, user.getUserId());

        try {
            executor.execute(() -> {
                try {
                    runService.executeStarted(started);

                } catch (RuntimeException exception) {
                    /*
                     * SQL별 일반 실패는 서비스 안에서 기록된다.
                     * 여기까지 전달된 장애는 저장 상태 확인이 필요할 수 있어
                     * 임의로 전체 FAILED 처리하거나 SQL을 재실행하지 않는다.
                     */
                    log.error(
                            "실행 묶음 처리가 중단되었습니다. batchId={}",
                            batchId,
                            exception);
                }
            });

        } catch (RejectedExecutionException exception) {
            // 작업을 실행하지 못한 경우 시작 상태를 원자적으로 복원한다.
            try {
                recordService.cancelRejectedStart(batchId);

            } catch (RuntimeException restoreException) {
                exception.addSuppressed(restoreException);

                log.error(
                        "접수 거절 후 상태 복원에 실패했습니다. batchId={}",
                        batchId,
                        exception);

                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "실행 접수 실패 후 상태를 복원하지 못했습니다. 관리자 확인이 필요합니다.",
                        exception);
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 다른 실행을 처리 중이거나 실행기가 종료 중입니다. 잠시 후 다시 시도하세요.",
                    exception);
        }
    }

    @Transactional(transactionManager = "managementTransactionManager", propagation = Propagation.NEVER)
    public void submitSingle(Long batchId, Long executionId, SqlFlowUserDetails user) {
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        var started = recordService.startSingle(batchId, executionId, user.getUserId());
        try {
            executor.execute(() -> {
                try { runService.executeSingleStarted(started); }
                catch (RuntimeException exception) {
                    log.error("개별 SQL 처리가 중단되었습니다. batchId={}, executionId={}", batchId, executionId, exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            try { recordService.cancelRejectedSingleStart(batchId, executionId); }
            catch (RuntimeException restoreException) {
                exception.addSuppressed(restoreException);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "개별 실행 접수 실패 후 상태를 복원하지 못했습니다.", exception);
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 실행기가 바쁩니다. 잠시 후 다시 시도하세요.", exception);
        }
    }
    
    /**
     * 미실행 SQL의 배치를 자동 생성하고 백그라운드 실행을 접수한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public BatchStartResult submitRemaining(
            Long requestId,
            SqlFlowUserDetails user) {

        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        // 이 메서드가 반환되면 예약 트랜잭션이 커밋된 상태다.
        var started = recordService.reserveRemaining(
                requestId, user.getUserId());

        try {
            executor.execute(() -> {
                try {
                    // 이미 예약했으므로 start()를 다시 호출하지 않는다.
                    // 일반 SQL 실패는 기록하고 다음 SQL을 계속 실행한다.
                    runService.executeStarted(started);
                } catch (RuntimeException exception) {
                    // 이력·결과 저장 장애는 임의 재실행이나 실패 덮어쓰기를 하지 않는다.
                    log.error(
                            "전체 SQL 처리가 중단되었습니다. requestId={}, batchId={}",
                            requestId, started.batchId(), exception);
                }
            });
        } catch (RejectedExecutionException exception) {
            try {
                // 실제 실행기에 접수되지 않은 자동 생성 배치만 복구한다.
                recordService.cancelRejectedRemainingStart(started);
            } catch (RuntimeException restoreException) {
                exception.addSuppressed(restoreException);

                log.error(
                        "전체 실행 접수 거절 후 복구 실패. requestId={}, batchId={}",
                        requestId, started.batchId(), exception);

                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "실행 접수 실패 후 예약을 복구하지 못했습니다. "
                                + "관리자 확인이 필요합니다.",
                        exception);
            }

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "현재 실행기가 다른 작업을 처리 중이거나 종료되었습니다. "
                            + "잠시 후 다시 시도하세요.",
                    exception);
        }

        return started;
    }
}
