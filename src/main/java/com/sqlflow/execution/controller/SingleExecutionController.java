package com.sqlflow.execution.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.SingleExecutionDispatchService;
import com.sqlflow.execution.service.SingleExecutionRecordService.SingleStartResult;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/executions")
@RequiredArgsConstructor
public class SingleExecutionController {

    private final SingleExecutionDispatchService dispatchService;

    @PostMapping("/requests/{requestId}/queries/{queryId}/run")
    public ResponseEntity<SingleStartResult> run(
            @PathVariable("requestId") Long requestId,
            @PathVariable("queryId") Long queryId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        var started =
                dispatchService.submit(requestId, queryId, user);

        // 실행 완료가 아니라 백그라운드 실행 접수 성공이다.
        return ResponseEntity.accepted().body(started);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of(
                        "message",
                        Objects.requireNonNullElse(
                                exception.getReason(),
                                "실행 요청을 처리하지 못했습니다.")));
    }
}