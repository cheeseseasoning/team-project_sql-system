package com.sqlflow.ai.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.ai.dto.SqlGenerationRequest;
import com.sqlflow.ai.dto.SqlGenerationResponse;
import com.sqlflow.ai.service.OpenAiSqlGenerationService;
import com.sqlflow.auth.security.SqlFlowUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai/sql")
@RequiredArgsConstructor
public class AiSqlController {

    private final OpenAiSqlGenerationService generationService;

    @PostMapping("/generate")
    public SqlGenerationResponse generate(
            @Valid @RequestBody SqlGenerationRequest request,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return generationService.generate(user.getUserId(), user.getDeptId(), request);
    }

    /** 서비스가 작성한 안내 사유를 기존 화면의 message 형식으로 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleGenerationError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of(
                "message",
                Objects.requireNonNullElse(exception.getReason(), "AI SQL 초안을 생성하지 못했습니다.")));
    }
}