package com.sqlflow.ai.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
