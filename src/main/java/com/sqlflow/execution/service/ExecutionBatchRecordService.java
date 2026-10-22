package com.sqlflow.execution.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;

import lombok.RequiredArgsConstructor;

/**
 * 실행 묶음과 요청 상태를 Oracle의 짧은 트랜잭션으로 변경한다.
 * MySQL SQL 실행과 파일 생성은 이 서비스 밖에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionBatchRecordService {

    private final ExecutionBatchMapper batchMapper;

    /**
     * 실행자 자격과 최종 승인 상태를 확인하고 묶음을 시작한다.
     * 같은 요청의 시작·완료 처리는 요청 행 잠금으로 직렬화한다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public BatchStartResult start(Long batchId, Long actorId) {

        if (batchId == null || batchId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "실행 묶음 ID가 올바르지 않습니다.");
        }

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        // 로그인 이후 역할이 회수된 경우도 반영한다.
        if (!batchMapper.isActiveDbApprover(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SQL 실행 권한이 없습니다.");
        }

        ExecutionBatch batch = requireBatch(batchId);

        var request = batchMapper.selectRequestContextForUpdate(
                batch.getRequestId());

        if (request == null
                || !"APPROVED".equals(request.getApprovalStatus())
                || !"READY".equals(request.getRequestStatus())) {
            throw conflict("최종 승인된 실행 대기 요청만 시작할 수 있습니다.");
        }

        if (!"INITIAL".equals(batch.getBatchType())) {
            throw conflict("현재는 최초 실행 묶음만 지원합니다.");
        }

        List<QueryExecution> executions = batchMapper.selectExecutions(batchId);
        validatePendingExecutions(batch.getRequestId(), executions);

        // 조건부 UPDATE로 같은 묶음의 중복 시작을 차단한다.
        if (batchMapper.markBatchRunning(batchId, actorId) != 1) {
            throw conflict("이미 시작되었거나 대기 상태가 아닌 실행 묶음입니다.");
        }

        if (batchMapper.updateRequestStatus(
                batch.getRequestId(), "READY", "RUNNING") != 1) {
            throw conflict("요청을 실행 중 상태로 변경할 수 없습니다.");
        }

        List<Long> executionIds = executions.stream()
                .map(QueryExecution::getExecutionId)
                .toList();

        return new BatchStartResult(
                batchId, batch.getRequestId(), executionIds);
    }

    /**
     * 메모리의 성공 개수 대신 DB에 저장된 SQL별 최종 상태를 집계한다.
     * 미처리 SQL이 있으면 완료 상태로 변경하지 않는다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public BatchExecutionResult finish(Long batchId) {

        ExecutionBatch batch = requireBatch(batchId);

        var request = batchMapper.selectRequestContextForUpdate(
                batch.getRequestId());

        if (request == null
                || !"RUNNING".equals(request.getRequestStatus())
                || !"APPROVED".equals(request.getApprovalStatus())) {
            throw conflict("실행 중인 승인 요청만 완료할 수 있습니다.");
        }

        List<QueryExecution> executions = batchMapper.selectExecutions(batchId);

        if (executions.isEmpty()) {
            throw conflict("집계할 SQL 실행 이력이 없습니다.");
        }

        int successCount = 0;
        int failureCount = 0;

        for (QueryExecution execution : executions) {
            if ("SUCCESS".equals(execution.getStatus())) {
                successCount++;
            } else if ("FAILED".equals(execution.getStatus())) {
                failureCount++;
            } else {
                throw conflict("아직 완료되지 않은 SQL이 있습니다.");
            }
        }

        String batchStatus;
        String requestStatus;

        if (failureCount == 0) {
            batchStatus = "SUCCESS";
            requestStatus = "COMPLETED";
        } else if (successCount == 0) {
            batchStatus = "FAILED";
            requestStatus = "FAILED";
        } else {
            batchStatus = "PARTIAL_FAILED";
            requestStatus = "PARTIAL_FAILED";
        }

        if (batchMapper.updateStatus(
                batchId, "RUNNING", batchStatus) != 1) {
            throw conflict("실행 묶음 상태가 변경되어 완료할 수 없습니다.");
        }

        if (batchMapper.updateRequestStatus(
                batch.getRequestId(), "RUNNING", requestStatus) != 1) {
            throw conflict("요청 상태가 변경되어 완료할 수 없습니다.");
        }

        return new BatchExecutionResult(
                batchId,
                batch.getRequestId(),
                batchStatus,
                requestStatus,
                successCount,
                failureCount);
    }

    private ExecutionBatch requireBatch(Long batchId) {
        ExecutionBatch batch = batchMapper.selectBatch(batchId);

        if (batch == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "실행 묶음을 찾을 수 없습니다.");
        }

        return batch;
    }

    /** 최초 실행은 요청의 SQL마다 대기 이력이 정확히 하나씩 있어야 한다. */
    private void validatePendingExecutions(Long requestId, List<QueryExecution> executions) {

        List<Long> queryIds = batchMapper.selectQueryIdsByRequestId(requestId);

        if (queryIds.isEmpty()
                || queryIds.size() > 5
                || executions.size() != queryIds.size()) {
            throw conflict("요청 SQL과 실행 이력의 개수가 일치하지 않습니다.");
        }

        var executionQueryIds = new HashSet<Long>();

        for (QueryExecution execution : executions) {
            if (execution.getExecutionId() == null
                    || !"PENDING".equals(execution.getStatus())
                    || !executionQueryIds.add(execution.getQueryId())) {
                throw conflict("중복되었거나 대기 상태가 아닌 실행 이력이 있습니다.");
            }
        }

        if (!executionQueryIds.equals(new HashSet<>(queryIds))) {
            throw conflict("요청 SQL과 실행 대상이 일치하지 않습니다.");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    public record BatchStartResult(
            Long batchId,
            Long requestId,
            List<Long> executionIds) {

        public BatchStartResult {
            executionIds = List.copyOf(executionIds);
        }
    }

    public record BatchExecutionResult(
            Long batchId,
            Long requestId,
            String batchStatus,
            String requestStatus,
            int successCount,
            int failureCount) {
    }
}