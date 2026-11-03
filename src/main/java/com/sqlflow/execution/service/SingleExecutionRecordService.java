package com.sqlflow.execution.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 배치 없는 개별 SQL 실행을 짧은 Oracle 트랜잭션으로 예약한다.
 * 실제 MySQL 실행은 이 서비스 밖에서 수행한다.
 */
@Service
@RequiredArgsConstructor
public class SingleExecutionRecordService {

    // 기존 요청 잠금·권한 확인 메서드를 재사용한다.
    private final ExecutionBatchMapper requestMapper;
    private final QueryExecutionMapper executionMapper;

    @Transactional(transactionManager = "managementTransactionManager")
    public SingleStartResult reserve(
            Long requestId,
            Long queryId,
            Long actorId) {

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (requestId == null || requestId <= 0
                || queryId == null || queryId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "실행 대상 ID가 올바르지 않습니다.");
        }

        // 세션 정보만 믿지 않고 현재 계정·역할을 DB에서 확인한다.
        if (!requestMapper.isActiveDbApprover(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "SQL 실행 권한이 없습니다.");
        }

        // 같은 기안서의 실행 접수를 직렬화한다.
        var request =
                requestMapper.selectRequestContextForUpdate(requestId);

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
                    "최종 승인된 미완료 요청의 SQL만 실행할 수 있습니다.");
        }

        var queryIds =
                requestMapper.selectQueryIdsByRequestId(requestId);

        if (!queryIds.contains(queryId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "해당 기안서에 속한 SQL을 찾을 수 없습니다.");
        }

        // 최초 실행만 지원한다. 실패 SQL 재실행은 별도 기능으로 처리한다.
        if (executionMapper.countExecutionsByQueryId(queryId) > 0) {
            throw conflict("이미 실행 이력이 있는 SQL입니다.");
        }

        if (executionMapper.countActiveExecutionsByRequestId(requestId) > 0) {
            throw conflict(
                    "이 기안서에 대기 또는 실행 중인 SQL이 있습니다.");
        }

        QueryExecution execution = new QueryExecution();
        execution.setBatchId(null);
        execution.setQueryId(queryId);
        execution.setExecutedBy(actorId);
        execution.setAttemptNo(1);
        execution.setRetryOfExecutionId(null);

        if (executionMapper.insertExecution(execution) != 1) {
            throw new IllegalStateException(
                    "개별 SQL 실행 이력을 생성하지 못했습니다.");
        }

        Long executionId = execution.getExecutionId();

        // 예약한 실행만 백그라운드 작업에 전달한다.
        if (executionMapper.markRunning(executionId) != 1) {
            throw conflict("개별 SQL 실행을 예약하지 못했습니다.");
        }

        if ("READY".equals(requestStatus)
                && requestMapper.updateRequestStatus(
                        requestId, "READY", "RUNNING") != 1) {
            throw conflict("요청을 실행 중 상태로 변경하지 못했습니다.");
        }

        return new SingleStartResult(
                requestId, queryId, executionId);
    }
    
    /**
     * 실행기가 작업 접수를 거절했을 때만 호출한다.
     * 이미 실행된 작업이나 사용자 취소에는 사용할 수 없다.
     */
    @Transactional(transactionManager = "managementTransactionManager")
    public void cancelRejectedStart(SingleStartResult started) {
        if (started == null
                || started.requestId() == null
                || started.queryId() == null
                || started.executionId() == null) {
            throw new IllegalArgumentException(
                    "복구할 실행 예약 정보가 필요합니다.");
        }

        // 예약할 때와 동일하게 기안서 행을 먼저 잠근다.
        var request = requestMapper.selectRequestContextForUpdate(
                started.requestId());

        if (request == null
                || !"APPROVED".equals(request.getApprovalStatus())
                || !"RUNNING".equals(request.getRequestStatus())) {
            throw conflict("실행 예약을 복구할 수 없는 요청 상태입니다.");
        }

        int deleted = executionMapper.deleteRejectedStandaloneReservation(
                started.requestId(),
                started.queryId(),
                started.executionId());

        if (deleted != 1) {
            throw conflict(
                    "실행 예약을 제거하지 못했습니다. 처리 상태를 확인해야 합니다.");
        }

        // 다른 실행 이력이 전혀 없으면 최초 실행 전 상태로 되돌린다.
        // 이전 개별 실행 이력이 있으면 RUNNING을 유지한다.
        if (executionMapper.countExecutionsByRequestId(
                started.requestId()) == 0) {

            if (requestMapper.updateRequestStatus(
                    started.requestId(), "RUNNING", "READY") != 1) {
                throw conflict("요청의 실행 대기 상태를 복원하지 못했습니다.");
            }
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT, message);
    }

    public record SingleStartResult(
            Long requestId,
            Long queryId,
            Long executionId) {
    }
}