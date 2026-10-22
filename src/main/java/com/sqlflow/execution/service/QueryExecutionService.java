package com.sqlflow.execution.service;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.domain.QueryExecutionTarget;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 SQL 한 개의 재검증·MySQL 실행·결과 기록을 연결한다.
 *
 * 실행자 권한 확인과 요청·묶음의 실행 시작은 상위 서비스에서 담당한다.
 * 이 서비스는 실행 중인 묶음에 속한 SQL을 처리한다.
 */
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private final QueryExecutionMapper queryExecutionMapper;
    private final RequestSqlValidationService requestSqlValidationService;
    private final MySqlQueryExecutionService mySqlQueryExecutionService;
    private final QueryExecutionRecordService recordService;

    /**
     * MySQL 조회 시간 동안 Oracle 트랜잭션을 유지하지 않는다.
     * 실행 기록은 별도 서비스의 짧은 트랜잭션으로 저장한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public ExecutionOutcome execute(Long executionId) {

        QueryExecutionTarget target = requireExecutionTarget(executionId);

        // 시작에 성공한 호출만 실제 실행을 진행한다.
        // 시작 실패 시 다른 작업의 실행 이력을 실패 처리하면 안 된다.
        recordService.start(executionId);

        long startedNanos = System.nanoTime();
        ExecutionResult result;

        try {
        	// 원래 요청자의 현재 접근 권한을 재검증한다.
        	requestSqlValidationService.validate(
        	        target.getDrafterId(),
        	        List.of(target.getSqlText()));

            result = mySqlQueryExecutionService.execute(target.getSqlText());

        } catch (ResponseStatusException exception) {
            return recordFailure(
                    executionId,
                    startedNanos,
                    "REVALIDATION_FAILED",
                    exception.getReason());

        } catch (SQLException exception) {
            return recordFailure(
                    executionId,
                    startedNanos,
                    "MYSQL_" + exception.getErrorCode(),
                    exception.getMessage());

        } catch (IOException exception) {
            return recordFailure(
                    executionId,
                    startedNanos,
                    "RESULT_FILE_ERROR",
                    exception.getMessage());

        } catch (RuntimeException exception) {
            // 예상하지 못한 오류도 기록하되 상위로 전달해 처리를 중단한다.
            recordUnexpectedFailure(executionId, startedNanos, exception);
            throw exception;
        }

        try {
            // 결과 정보와 성공 상태를 같은 Oracle 트랜잭션으로 저장한다.
            recordService.complete(executionId, result);

        } catch (DataIntegrityViolationException exception) {
            // 제약 위반으로 저장이 롤백된 경우 실패를 기록하고 파일을 정리한다.
            ExecutionOutcome outcome = recordFailure(
                    executionId,
                    startedNanos,
                    "RESULT_SAVE_FAILED",
                    exception.getMessage());

            deleteResultFile(result, exception);

            return outcome;

        } catch (RuntimeException exception) {
            /*
             * 연결 단절·커밋 응답 유실 시 저장 성공 여부가 불분명할 수 있다.
             * 성공 결과를 삭제하지 않도록 파일을 보존하고 장애를 전달한다.
             */
            throw new IllegalStateException(
                    "결과 저장 상태를 확인해야 합니다. executionId="
                            + executionId
                            + ", relativePath="
                            + result.relativePath(),
                    exception);
        }

        return new ExecutionOutcome(executionId, true);
    }

    /** 최종 승인된 실행 중 묶음에 속한 대기 SQL인지 확인한다. */
    private QueryExecutionTarget requireExecutionTarget(Long executionId) {

        if (executionId == null || executionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "실행 이력 ID가 올바르지 않습니다.");
        }

        QueryExecutionTarget target =
                queryExecutionMapper.selectExecutionTarget(executionId);

        if (target == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "실행 대상을 찾을 수 없습니다.");
        }

        if (!"PENDING".equals(target.getExecutionStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "대기 상태의 SQL만 실행할 수 있습니다.");
        }

        if (!"APPROVED".equals(target.getApprovalStatus())
                || !"RUNNING".equals(target.getRequestStatus())
                || !"RUNNING".equals(target.getBatchStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "최종 승인 후 시작된 실행 묶음이 필요합니다.");
        }

        if (target.getDrafterId() == null
                || target.getDrafterId() <= 0
                || target.getSqlText() == null
                || target.getSqlText().isBlank()) {
            throw new IllegalStateException(
                    "요청자 또는 저장된 SQL 정보가 올바르지 않습니다.");
        }

        return target;
    }

    /**
     * SQL·검증·파일 처리 실패를 기록한다.
     * 실패 기록 자체가 불가능하면 예외를 전달해 상위 처리를 중단한다.
     */
    private ExecutionOutcome recordFailure(
            Long executionId,
            long startedNanos,
            String errorCode,
            String errorMessage) {

        long durationMs = elapsedMillis(startedNanos);

        recordService.fail(
                executionId,
                durationMs,
                errorCode,
                errorMessage == null ? "상세 오류 정보 없음" : errorMessage);

        return new ExecutionOutcome(executionId, false);
    }

    /** 실패 기록 오류가 최초 발생한 오류를 덮어쓰지 않도록 한다. */
    private void recordUnexpectedFailure(
            Long executionId,
            long startedNanos,
            RuntimeException original) {

        try {
            recordFailure(
                    executionId,
                    startedNanos,
                    "EXECUTION_ERROR",
                    original.getMessage());

        } catch (RuntimeException recordException) {
            original.addSuppressed(recordException);
        }
    }

    /** 결과 정보 저장에 실패한 파일을 정리한다. */
    private void deleteResultFile(
            ExecutionResult result, RuntimeException original) {

        try {
            mySqlQueryExecutionService.deleteResultFile(result);

        } catch (IOException | RuntimeException cleanupException) {
            original.addSuppressed(cleanupException);

            throw new IllegalStateException(
                    "결과 저장 실패 후 파일을 정리하지 못했습니다. relativePath="
                            + result.relativePath(),
                    original);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** 실행 묶음에서 SQL별 성공·실패를 집계할 때 사용한다. */
    public record ExecutionOutcome(Long executionId, boolean success) {
    }
}