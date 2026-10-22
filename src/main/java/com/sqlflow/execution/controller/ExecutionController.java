package com.sqlflow.execution.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.ExecutionStatus;
import com.sqlflow.execution.dto.ExecutionBatchStatusResponseDTO;
import com.sqlflow.execution.dto.ExecutionQueueDetailResponse;
import com.sqlflow.execution.dto.ExecutionQueuePageResponse;
import com.sqlflow.execution.service.ExecutionBatchDispatchService;
import com.sqlflow.execution.service.ExecutionBatchService;
import com.sqlflow.execution.service.ExecutionBatchStatusService;
import com.sqlflow.execution.service.ExecutionQueueService;

import lombok.RequiredArgsConstructor;

/**
 * SQL 실행 대기 등록 API.
 * 실행자 정보는 요청 본문이 아닌 서버의 로그인 정보에서 가져온다.
 */
@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class ExecutionController {

    private final ExecutionBatchService executionBatchService;
    private final ExecutionBatchDispatchService executionBatchDispatchService;
    private final ExecutionBatchStatusService executionBatchStatusService;
    private final ExecutionQueueService executionQueueService;

    /** 승인된 요청의 최초 실행 묶음과 SQL별 대기 이력을 등록한다. */
    @PostMapping("/requests/{requestId}/batches")
    public ResponseEntity<BatchCreateResponse> createInitialBatch(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        Long batchId = executionBatchService.createInitialBatch(requestId, user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BatchCreateResponse(batchId, ExecutionStatus.PENDING.name()));
    }
    
    /**
     * 등록된 실행 묶음을 백그라운드 실행으로 접수한다.
     * 202는 실행 완료가 아닌 접수 성공을 의미한다.
     */
    @PostMapping("/batches/{batchId}/run")
    public ResponseEntity<BatchAcceptedResponse> runBatch(
            @PathVariable("batchId") Long batchId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        executionBatchDispatchService.submit(batchId, user);

        return ResponseEntity.accepted().body(new BatchAcceptedResponse(batchId, "실행 요청이 접수되었습니다."));
    }

    @PostMapping("/batches/{batchId}/executions/{executionId}/run")
    public ResponseEntity<BatchAcceptedResponse> runSingle(
            @PathVariable Long batchId,
            @PathVariable Long executionId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        executionBatchDispatchService.submitSingle(batchId, executionId, user);
        return ResponseEntity.accepted().body(new BatchAcceptedResponse(batchId, "SQL 실행 요청이 접수되었습니다."));
    }

    public record BatchAcceptedResponse(Long batchId, String message) {
    }

    /** 등록한 실행 묶음의 ID와 현재 상태를 화면에 전달한다. */
    public record BatchCreateResponse(Long batchId, String batchStatus) {
    }
    
    /** 저장된 실행 이력을 조회하며 SQL을 다시 실행하지 않는다. */
    @GetMapping("/batches/{batchId}")
    public ExecutionBatchStatusResponseDTO getBatchStatus(
            @PathVariable("batchId") Long batchId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return executionBatchStatusService.getStatus(batchId, user);
    }

    @GetMapping("/ready")
    public ExecutionQueuePageResponse getReadyRequests(
            @AuthenticationPrincipal SqlFlowUserDetails user,
            @RequestParam(name="page", defaultValue="1") int page,
            @RequestParam(name="size", defaultValue="10") int size,
            @RequestParam(name="keyword", required=false) String keyword) {
        return executionQueueService.getQueue(user, page, size, keyword);
    }

    @GetMapping("/requests/{requestId}")
    public ExecutionQueueDetailResponse getRequestExecutionDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return executionQueueService.getDetail(user, requestId);
    }

    /** Service에서 발생한 업무 오류를 상태 코드와 안내 메시지로 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleExecutionError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", Objects.requireNonNullElse(exception.getReason(), "실행 요청을 처리할 수 없습니다.")));
    }
}
