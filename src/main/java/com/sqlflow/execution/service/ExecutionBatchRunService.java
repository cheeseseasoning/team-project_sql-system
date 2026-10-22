package com.sqlflow.execution.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchExecutionResult;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchStartResult;

import lombok.RequiredArgsConstructor;

/**
 * 실행 묶음을 시작하고 SQL을 순서대로 처리한 후 최종 상태를 집계한다.
 * 전체 실행을 하나의 Oracle 트랜잭션으로 묶지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionBatchRunService {

    private final ExecutionBatchRecordService batchRecordService;
    private final QueryExecutionService queryExecutionService;

    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public BatchExecutionResult execute(
            Long batchId, SqlFlowUserDetails user) {

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // 실행자 ID는 서버의 로그인 정보에서 가져온다.
        // 활성 계정과 현재 DB_APPROVER 역할은 start()에서 DB로 재확인한다.
        var started = batchRecordService.start(batchId, user.getUserId());

        return executeStarted(started);
    }
    
    /**
     * Dispatch에서 시작 처리를 마친 묶음을 백그라운드로 실행한다.
     * 시작 처리를 다시 하지 않고 SQL 실행과 최종 집계만 수행한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public BatchExecutionResult executeStarted(BatchStartResult started) {

        for (Long executionId : started.executionIds()) {
            // 일반 SQL 실패는 기록 후 계속 진행하고, 기록 장애는 예외로 중단한다.
            queryExecutionService.execute(executionId);
        }

        return batchRecordService.finish(started.batchId());
    }
}