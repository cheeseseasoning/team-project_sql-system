package com.sqlflow.execution.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchExecutionResult;

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

        for (Long executionId : started.executionIds()) {
            /*
             * 일반 SQL·검증·파일 오류는 실패 결과로 반환되므로 계속 진행한다.
             * DB 기록 장애나 저장 상태 불명확 오류는 예외가 전달되어 중단된다.
             */
            queryExecutionService.execute(executionId);
        }

        return batchRecordService.finish(batchId);
    }
}