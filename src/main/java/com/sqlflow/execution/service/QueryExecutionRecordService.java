package com.sqlflow.execution.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.execution.ExecutionStatus;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.domain.QueryResult;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;

import lombok.RequiredArgsConstructor;

/**
 * SQL별 실행 상태와 결과 메타데이터를 Oracle에 기록한다.
 * MySQL 조회와 파일 생성은 이 서비스의 트랜잭션 밖에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class QueryExecutionRecordService {

    private final QueryExecutionMapper queryExecutionMapper;
    private final QueryResultMapper queryResultMapper;

    /**
     * 대기 상태의 실행을 시작한다.
     * 상태 변경에 성공한 호출만 실제 SQL 실행을 진행해야 한다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public void start(Long executionId) {
        if (queryExecutionMapper.markRunning(executionId) != 1) {
            throw new IllegalStateException(
                    "대기 상태가 아니거나 이미 시작된 실행입니다. executionId=" + executionId);
        }
    }

    /**
     * 결과 파일 정보와 SQL 성공 상태를 함께 저장한다.
     * 어느 한쪽이 실패하면 둘 다 롤백한다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public void complete(Long executionId, ExecutionResult result) {

        QueryExecution execution = new QueryExecution();
        execution.setExecutionId(executionId);
        execution.setStatus(ExecutionStatus.SUCCESS.name());
        execution.setDurationMs(result.durationMs());

        if (queryExecutionMapper.finishExecution(execution) != 1) {
            throw new IllegalStateException(
                    "실행 중인 SQL만 완료할 수 있습니다. executionId=" + executionId);
        }

        QueryResult queryResult = new QueryResult();
        queryResult.setExecutionId(executionId);
        queryResult.setFilePath(result.relativePath());
        queryResult.setRowCount(result.rowCount());
        queryResult.setFileSize(result.fileSize());

        if (queryResultMapper.insertResult(queryResult) != 1) {
            throw new IllegalStateException("결과 파일 정보 저장에 실패했습니다.");
        }
    }

    /** 실패한 SQL의 소요 시간과 오류 내용을 기록한다. */
    @Transactional(transactionManager = "managementTransactionManager")
    public void fail(Long executionId, long durationMs, String errorCode, String errorMessage) {

        QueryExecution execution = new QueryExecution();
        execution.setExecutionId(executionId);
        execution.setStatus(ExecutionStatus.FAILED.name());
        execution.setDurationMs(durationMs);
        execution.setErrorCode(errorCode);
        execution.setErrorMessage(errorMessage);

        if (queryExecutionMapper.finishExecution(execution) != 1) {
            throw new IllegalStateException(
                    "실행 중인 SQL만 실패 처리할 수 있습니다. executionId=" + executionId);
        }
    }
}
