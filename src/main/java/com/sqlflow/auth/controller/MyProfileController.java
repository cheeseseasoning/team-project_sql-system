package com.sqlflow.auth.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.MyProfileResponse;
import com.sqlflow.auth.dto.MyProfileUpdateRequest;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.MyProfileService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class MyProfileController {

    private final MyProfileService myProfileService;

    @GetMapping
    public MyProfileResponse getProfile(
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        // 조회 대상 ID를 URL이나 요청 파라미터로 받지 않는다.
        return myProfileService.getProfile(user.getUserId());
    }
    
    @PatchMapping
    public MyProfileResponse updateContact(
            @Valid @RequestBody MyProfileUpdateRequest input,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        return myProfileService.updateContact(
                user.getUserId(), input);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(
            MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getDefaultMessage())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("연락처 입력값을 확인하세요.");

        return ResponseEntity.badRequest()
                .body(Map.of("message", message));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(Map.of(
                        "message",
                        Objects.requireNonNullElse(
                                exception.getReason(),
                                "내 정보를 조회하지 못했습니다.")));
    }
}