package com.sqlflow.execution.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 배치 유무와 관계없이 SQL별 최신 실행 상태로
 * 기안서 전체의 처리 상태를 계산한다.
 */
@Service
@RequiredArgsConstructor
public class RequestExecutionStatusService {

    private final ExecutionBatchMapper requestMapper;
    private final QueryExecutionMapper executionMapper;

    @Transactional(transactionManager = "managementTransactionManager")
    public String refresh(Long requestId) {
        if (requestId == null || requestId <= 0) {
            throw new IllegalArgumentException(
                    "기안서 ID가 올바르지 않습니다.");
        }

        var request =
                requestMapper.selectRequestContextForUpdate(requestId);

        if (request == null
                || !"APPROVED".equals(request.getApprovalStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "최종 승인된 조회 요청이 필요합니다.");
        }

        String currentStatus = request.getRequestStatus();

        // 이미 완료 집계된 경우에는 상태를 다시 변경하지 않는다.
        if ("COMPLETED".equals(currentStatus)
                || "PARTIAL_FAILED".equals(currentStatus)
                || "FAILED".equals(currentStatus)) {
            return currentStatus;
        }

        if (!"RUNNING".equals(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "실행 중인 요청만 집계할 수 있습니다.");
        }

        var executions =
                executionMapper.selectLatestExecutionsByRequestId(
                        requestId);

        if (executions.isEmpty()) {
            throw new IllegalStateException(
                    "기안서에 집계할 SQL이 없습니다.");
        }

        int successCount = 0;
        int failureCount = 0;
        boolean unfinished = false;

        for (var execution : executions) {
            String status = execution.getStatus();

            if ("SUCCESS".equals(status)) {
                successCount++;
            } else if ("FAILED".equals(status)) {
                failureCount++;
            } else if (status == null
                    || "PENDING".equals(status)
                    || "RUNNING".equals(status)) {
                unfinished = true;
            } else {
                throw new IllegalStateException(
                        "지원하지 않는 SQL 실행 상태입니다: " + status);
            }
        }

        // 미실행 SQL까지 모두 처리되기 전에는 최종 완료하지 않는다.
        if (unfinished) {
            return currentStatus;
        }

        String nextStatus;

        if (failureCount == 0) {
            nextStatus = "COMPLETED";
        } else if (successCount == 0) {
            nextStatus = "FAILED";
        } else {
            nextStatus = "PARTIAL_FAILED";
        }

        if (requestMapper.updateRequestStatus(
                requestId, "RUNNING", nextStatus) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "기안서 실행 상태를 갱신하지 못했습니다.");
        }

        return nextStatus;
    }
}