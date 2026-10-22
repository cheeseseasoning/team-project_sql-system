package com.sqlflow.execution.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionResultPreviewResponse;
import com.sqlflow.execution.service.CsvPreviewService;

import lombok.RequiredArgsConstructor;

/**
 * 샘플 CSV 확인용 개발 API.
 * csv-preview 프로필에서만 활성화한다.
 */
@Profile("csv-preview")
@RestController
@RequestMapping("/api/execution-results/sample")
@RequiredArgsConstructor
public class CsvPreviewController {

    private final CsvPreviewService csvPreviewService;

    @GetMapping("/preview")
    public ExecutionResultPreviewResponse preview(
            @AuthenticationPrincipal SqlFlowUserDetails user,
            @RequestParam(name = "offset", defaultValue = "0") long offset,
            @RequestParam(name = "limit", defaultValue = "25") int limit) {

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다."
            );
        }

        // 파일 경로는 요청으로 받지 않고 샘플 파일로 고정한다.
        return csvPreviewService.read("sample.csv", offset, limit);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(
            ResponseStatusException exception) {

        return ResponseEntity
                .status(exception.getStatusCode())
                .body(Map.of(
                        "message",
                        Objects.requireNonNullElse(
                                exception.getReason(),
                                "미리보기를 불러오지 못했습니다."
                        )
                ));
    }
}