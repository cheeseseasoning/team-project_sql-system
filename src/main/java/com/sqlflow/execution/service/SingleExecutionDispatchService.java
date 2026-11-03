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
import com.sqlflow.execution.service.SingleExecutionRecordService.SingleStartResult;

import lombok.extern.slf4j.Slf4j;

/**
 * 개별 SQL의 예약을 커밋한 뒤 백그라운드 실행기에 전달한다.
 */
@Slf4j
@Service
public class SingleExecutionDispatchService {

    private final SingleExecutionRecordService recordService;
    private final QueryExecutionService queryExecutionService;
    private final RequestExecutionStatusService statusService;
    private final Executor executor;

    public SingleExecutionDispatchService(
            SingleExecutionRecordService recordService,
            QueryExecutionService queryExecutionService,
            RequestExecutionStatusService statusService,
            @Qualifier("sqlExecutionExecutor") Executor executor) {
        this.recordService = recordService;
        this.queryExecutionService = queryExecutionService;
        this.statusService = statusService;
        this.executor = executor;
    }

    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public SingleStartResult submit(
            Long requestId,
            Long queryId,
            SqlFlowUserDetails user) {

        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        // 다른 빈의 트랜잭션 메서드 호출이 끝나면서 예약이 커밋된다.
        var started = recordService.reserve(
                requestId, queryId, user.getUserId());

        try {
            executor.execute(() -> executeReserved(started));
        } catch (RejectedExecutionException exception) {
            // 실행기가 작업을 받지 않은 경우에만 예약을 복구한다.
            try {
                recordService.cancelRejectedStart(started);
            } catch (RuntimeException restoreException) {
                exception.addSuppressed(restoreException);

                log.error(
                        "개별 실행 접수 거절 후 복구 실패. requestId={}, executionId={}",
                        requestId, started.executionId(), exception);

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

    private void executeReserved(SingleStartResult started) {
        try {
            // 기존 SQL 검증·MySQL 실행·결과 저장 로직을 재사용한다.
            queryExecutionService.executeStarted(started.executionId());
        } catch (RuntimeException exception) {
            // 처리 결과가 불명확할 수 있으므로 임의로 실패 처리하지 않는다.
            log.error(
                    "개별 SQL 처리 중 장애. requestId={}, executionId={}",
                    started.requestId(), started.executionId(), exception);
        } finally {
            try {
                // 예외 유무가 아니라 DB에 확정된 SQL별 상태로 집계한다.
                // RUNNING이 남아 있으면 기안서도 완료하지 않는다.
                statusService.refresh(started.requestId());
            } catch (RuntimeException exception) {
                log.error(
                        "개별 실행 후 기안서 상태 집계 실패. requestId={}, executionId={}",
                        started.requestId(), started.executionId(), exception);
            }
        }
    }
}