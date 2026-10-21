package com.sqlflow.execution.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionResultPageResponse;
import com.sqlflow.execution.service.ExecutionResultService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/execution-results")
@RequiredArgsConstructor
public class ExecutionResultController {

    private final ExecutionResultService executionResultService;

    @GetMapping("/mine")
    public ExecutionResultPageResponse getMyResults(
            @AuthenticationPrincipal SqlFlowUserDetails user,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        return executionResultService.getMyResults(
                user == null ? null : user.getUserId(),
                page,
                size);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(
            ResponseStatusException exception) {

        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of(
                        "message",
                        Objects.requireNonNullElse(
                                exception.getReason(),
                                "실행 결과 목록을 조회하지 못했습니다.")));
    }
}