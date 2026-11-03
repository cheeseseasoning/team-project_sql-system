package com.sqlflow.execution.service;

import java.util.ArrayList;
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
    
    private final com.sqlflow.execution.mapper.QueryExecutionMapper queryExecutionMapper;
    
    private final RequestExecutionStatusService requestStatusService;

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

    /** Lock and reserve one pending SQL while the request remains READY. */
    @Transactional(transactionManager = "managementTransactionManager")
    public SingleStartResult startSingle(Long batchId, Long executionId, Long actorId) {
        if (batchId == null || batchId <= 0 || executionId == null || executionId <= 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "실행 대상 ID가 올바르지 않습니다.");
        if (actorId == null || actorId <= 0)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        if (!batchMapper.isActiveDbApprover(actorId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SQL 실행 권한이 없습니다.");
        ExecutionBatch batch = requireBatch(batchId);
        var request = batchMapper.selectRequestContextForUpdate(batch.getRequestId());
        if (request == null || !"APPROVED".equals(request.getApprovalStatus())
                || !("READY".equals(request.getRequestStatus()) || "RUNNING".equals(request.getRequestStatus())))
            throw conflict("최종 승인된 실행 대기 요청의 SQL만 개별 실행할 수 있습니다.");
        if (!"INITIAL".equals(batch.getBatchType()) || !"PENDING".equals(batch.getBatchStatus()))
            throw conflict("전체 실행이 시작된 묶음에서는 개별 실행할 수 없습니다.");
        var target = queryExecutionMapper.selectExecutionTarget(executionId);
        if (target == null || !batchId.equals(target.getBatchId()) || !batch.getRequestId().equals(target.getRequestId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "요청에 속한 실행 대상을 찾을 수 없습니다.");
        if (!"PENDING".equals(target.getExecutionStatus()))
            throw conflict("대기 상태의 SQL만 실행할 수 있습니다.");
        if (queryExecutionMapper.markRunning(executionId) != 1)
            throw conflict("다른 실행 작업이 먼저 시작했습니다.");
        if ("READY".equals(request.getRequestStatus())
                && batchMapper.updateRequestStatus(batch.getRequestId(), "READY", "RUNNING") != 1)
            throw conflict("요청을 실행 중 상태로 변경할 수 없습니다.");
        return new SingleStartResult(batchId, batch.getRequestId(), executionId);
    }

    /** Release a rejected single-run task and restore READY only when no other item started. */
    @Transactional(transactionManager = "managementTransactionManager")
    public void cancelRejectedSingleStart(Long batchId, Long executionId) {
        ExecutionBatch batch = requireBatch(batchId);
        var request = batchMapper.selectRequestContextForUpdate(batch.getRequestId());
        if (request == null || !"APPROVED".equals(request.getApprovalStatus())
                || !"RUNNING".equals(request.getRequestStatus())
                || !"PENDING".equals(batch.getBatchStatus()))
            throw conflict("접수 실패한 SQL 상태를 복원할 수 없습니다.");
        if (queryExecutionMapper.resetRejectedReservation(executionId) != 1)
            throw conflict("접수되지 않은 SQL 예약을 해제할 수 없습니다.");
        List<QueryExecution> executions = batchMapper.selectExecutions(batchId);
        if (!executions.isEmpty() && executions.stream().allMatch(e -> "PENDING".equals(e.getStatus()))) {
            if (batchMapper.updateRequestStatus(batch.getRequestId(), "RUNNING", "READY") != 1)
                throw conflict("요청의 실행 대기 상태를 복원할 수 없습니다.");
        }
    }

    /**
     * 메모리의 성공 개수 대신 DB에 저장된 SQL별 최종 상태를 집계한다.
     * 미처리 SQL이 있으면 완료 상태로 변경하지 않는다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public BatchExecutionResult finish(Long batchId) {
        return finishWhenComplete(batchId, true);
    }

    /** Finalize a batch after individual runs only when every SQL reached a terminal state. */
    @Transactional(transactionManager = "managementTransactionManager")
    public BatchExecutionResult finishIfComplete(Long batchId) {
        return finishWhenComplete(batchId, false);
    }

    private BatchExecutionResult finishWhenComplete(Long batchId, boolean requireWholeBatchRun) {

        ExecutionBatch batch = requireBatch(batchId);

        var request = batchMapper.selectRequestContextForUpdate(
                batch.getRequestId());

        if (request == null || !"APPROVED".equals(request.getApprovalStatus())
                || !("RUNNING".equals(request.getRequestStatus()) || "READY".equals(request.getRequestStatus()))) {
            throw conflict("실행 중인 승인 요청만 완료할 수 있습니다.");
        }
        
        String expectedBatchStatus = batch.getBatchStatus();
        String expectedRequestStatus = request.getRequestStatus();
        
        boolean wholeBatchRun = "RUNNING".equals(expectedBatchStatus) && "RUNNING".equals(expectedRequestStatus);
        boolean individualRun = "PENDING".equals(expectedBatchStatus) && "RUNNING".equals(expectedRequestStatus);
        
        if (requireWholeBatchRun && !wholeBatchRun)
            throw conflict("실행 중인 묶음만 완료할 수 있습니다.");
        if (!requireWholeBatchRun && !wholeBatchRun && !individualRun)
            throw conflict("실행 중인 SQL이 있는 요청만 완료할 수 있습니다.");

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
                if (!requireWholeBatchRun) return null;
                throw conflict("아직 완료되지 않은 SQL이 있습니다.");
            }
        }

        String batchStatus;

        if (failureCount == 0) {
            batchStatus = "SUCCESS";
        } else if (successCount == 0) {
            batchStatus = "FAILED";
        } else {
            batchStatus = "PARTIAL_FAILED";
        }

        if (batchMapper.updateStatus(
                batchId, expectedBatchStatus, batchStatus) != 1) {
            throw conflict("실행 묶음 상태가 변경되어 완료할 수 없습니다.");
        }

        // 배치에 속하지 않은 개별 실행까지 포함해서 기안서를 집계한다.
        // 같은 관리 DB 트랜잭션에 참여하므로 집계 실패 시 배치 변경도 롤백된다.
        String requestStatus =
                requestStatusService.refresh(batch.getRequestId());

        return new BatchExecutionResult(
                batchId,
                batch.getRequestId(),
                batchStatus,
                requestStatus,
                successCount,
                failureCount);
    }
    
    /**
     * 백그라운드 작업 접수 자체가 거절된 경우에만 시작 상태를 되돌린다.
     * 이미 SQL 처리가 시작된 묶음에는 사용할 수 없다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public void cancelRejectedStart(Long batchId) {

        ExecutionBatch batch = requireBatch(batchId);

        var request = batchMapper.selectRequestContextForUpdate(
                batch.getRequestId());

        if (request == null
                || !"RUNNING".equals(request.getRequestStatus())
                || !"APPROVED".equals(request.getApprovalStatus())) {
            throw conflict("접수 실패한 요청의 상태를 복원할 수 없습니다.");
        }

        List<QueryExecution> executions = batchMapper.selectExecutions(batchId);

        if (executions.isEmpty()
                || executions.stream().anyMatch(
                        execution -> !"PENDING".equals(execution.getStatus()))) {
            throw conflict("SQL 처리가 시작된 묶음은 접수 취소할 수 없습니다.");
        }

        if (batchMapper.updateStatus(batchId, "RUNNING", "PENDING") != 1) {
            throw conflict("실행 묶음의 대기 상태를 복원할 수 없습니다.");
        }

        if (batchMapper.updateRequestStatus(
                batch.getRequestId(), "RUNNING", "READY") != 1) {
            throw conflict("요청의 실행 대기 상태를 복원할 수 없습니다.");
        }
    }
    
    /**
     * 미실행 SQL만 대상으로 최초 전체 실행 배치를 생성하고 예약한다.
     * 개별 실행과 동일한 요청 행 잠금으로 중복 접수를 막는다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public BatchStartResult reserveRemaining(Long requestId, Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (requestId == null || requestId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "기안서 ID가 올바르지 않습니다.");
        }

        if (!batchMapper.isActiveDbApprover(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "SQL 실행 권한이 없습니다.");
        }

        var request =
                batchMapper.selectRequestContextForUpdate(requestId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        String requestStatus = request.getRequestStatus();

        if (!"APPROVED".equals(request.getApprovalStatus())
                || !("READY".equals(requestStatus)
                    || "RUNNING".equals(requestStatus))) {
            throw conflict(
                    "최종 승인된 미완료 요청만 실행할 수 있습니다.");
        }

        if (queryExecutionMapper.countActiveExecutionsByRequestId(
                requestId) > 0) {
            throw conflict(
                    "이 기안서에 대기 또는 실행 중인 SQL이 있습니다.");
        }

        // QUERY_ORDER 순서로 반환되는 SQL별 최신 실행 정보.
        var latest =
                queryExecutionMapper.selectLatestExecutionsByRequestId(
                        requestId);

        // 성공·실패를 포함해 실행 이력이 있는 SQL은 제외한다.
        List<Long> queryIds = latest.stream()
                .filter(execution -> execution.getExecutionId() == null)
                .map(QueryExecution::getQueryId)
                .toList();

        if (queryIds.isEmpty()) {
            throw conflict("실행할 미실행 SQL이 없습니다.");
        }

        // 현재는 최초 전체 실행만 지원한다.
        // 기존 대기 배치는 기존 실행 경로로 처리한다.
        if (batchMapper.countBatchesByRequestId(requestId) > 0) {
            throw conflict(
                    "이미 실행 배치가 있는 요청입니다. 기존 배치 상태를 확인하세요.");
        }

        ExecutionBatch batch = new ExecutionBatch();
        batch.setRequestId(requestId);
        batch.setInitiatedBy(actorId);
        batch.setTriggerType("MANUAL");
        batch.setBatchType("INITIAL");
        batch.setBatchStatus("PENDING");

        if (batchMapper.insertBatch(batch) != 1) {
            throw new IllegalStateException(
                    "전체 실행 배치를 생성하지 못했습니다.");
        }

        List<Long> executionIds = new ArrayList<>();

        for (Long queryId : queryIds) {
            QueryExecution execution = new QueryExecution();
            execution.setBatchId(batch.getBatchId());
            execution.setQueryId(queryId);
            execution.setExecutedBy(actorId);
            execution.setAttemptNo(1);
            execution.setRetryOfExecutionId(null);

            if (queryExecutionMapper.insertExecution(execution) != 1) {
                throw new IllegalStateException(
                        "SQL 실행 이력을 생성하지 못했습니다. queryId="
                                + queryId);
            }

            executionIds.add(execution.getExecutionId());
        }

        if (batchMapper.markBatchRunning(
                batch.getBatchId(), actorId) != 1) {
            throw conflict("전체 실행 배치를 예약하지 못했습니다.");
        }

        if ("READY".equals(requestStatus)
                && batchMapper.updateRequestStatus(
                        requestId, "READY", "RUNNING") != 1) {
            throw conflict("기안서를 실행 중 상태로 변경하지 못했습니다.");
        }

        return new BatchStartResult(
                batch.getBatchId(), requestId, executionIds);
    }
    
    /**
     * 자동 생성한 전체 실행 배치를 실행기가 거절한 경우에만 호출한다.
     * 실제 SQL 실행이 시작된 배치에는 사용할 수 없다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public void cancelRejectedRemainingStart(BatchStartResult started) {
        if (started == null
                || started.batchId() == null
                || started.requestId() == null
                || started.executionIds().isEmpty()) {
            throw new IllegalArgumentException(
                    "복구할 전체 실행 예약 정보가 필요합니다.");
        }

        var request = batchMapper.selectRequestContextForUpdate(
                started.requestId());

        if (request == null
                || !"APPROVED".equals(request.getApprovalStatus())
                || !"RUNNING".equals(request.getRequestStatus())) {
            throw conflict("전체 실행 예약을 복구할 수 없는 요청 상태입니다.");
        }

        ExecutionBatch batch = requireBatch(started.batchId());

        if (!started.requestId().equals(batch.getRequestId())
                || !"INITIAL".equals(batch.getBatchType())
                || !"RUNNING".equals(batch.getBatchStatus())) {
            throw conflict("복구할 실행 배치 정보가 일치하지 않습니다.");
        }

        List<QueryExecution> executions =
                batchMapper.selectExecutions(started.batchId());

        var actualIds = new HashSet<>(
                executions.stream()
                        .map(QueryExecution::getExecutionId)
                        .toList());

        var reservedIds = new HashSet<>(started.executionIds());

        if (executions.size() != started.executionIds().size()
                || !actualIds.equals(reservedIds)
                || executions.stream().anyMatch(
                        execution -> !"PENDING".equals(execution.getStatus()))) {
            throw conflict(
                    "실행 대상이 변경됐거나 SQL 처리가 시작되어 복구할 수 없습니다.");
        }

        int deleted = batchMapper.deleteRejectedBatchExecutions(
                started.batchId());

        if (deleted != executions.size()) {
            throw conflict("배치 실행 예약을 모두 제거하지 못했습니다.");
        }

        if (batchMapper.deleteRejectedBatch(started.batchId()) != 1) {
            throw conflict("자동 생성한 실행 배치를 제거하지 못했습니다.");
        }

        // 앞서 실행한 개별 이력이 있으면 보존하고 요청은 RUNNING을 유지한다.
        if (queryExecutionMapper.countExecutionsByRequestId(
                started.requestId()) == 0) {

            if (batchMapper.updateRequestStatus(
                    started.requestId(), "RUNNING", "READY") != 1) {
                throw conflict("기안서의 실행 대기 상태를 복원하지 못했습니다.");
            }
        }
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

    public record SingleStartResult(Long batchId, Long requestId, Long executionId) { }
}
