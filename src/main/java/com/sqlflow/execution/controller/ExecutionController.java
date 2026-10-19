package com.sqlflow.execution.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.ExecutionStatus;
import com.sqlflow.execution.service.ExecutionBatchService;

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

    /** 승인된 요청의 최초 실행 묶음과 SQL별 대기 이력을 등록한다. */
    @PostMapping("/requests/{requestId}/batches")
    public ResponseEntity<BatchCreateResponse> createInitialBatch(
            @PathVariable("requestId") Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        Long batchId = executionBatchService.createInitialBatch(requestId, user);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BatchCreateResponse(batchId, ExecutionStatus.PENDING.name()));
    }

    /** 등록한 실행 묶음의 ID와 현재 상태를 화면에 전달한다. */
    public record BatchCreateResponse(Long batchId, String batchStatus) {
    }

    /** Service에서 발생한 업무 오류를 상태 코드와 안내 메시지로 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleExecutionError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of("message", Objects.requireNonNullElse(exception.getReason(), "실행 요청을 처리할 수 없습니다.")));
    }
}