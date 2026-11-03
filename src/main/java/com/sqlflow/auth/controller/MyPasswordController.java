package com.sqlflow.auth.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.MyPasswordChangeRequest;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.MyPasswordService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/me/password")
@RequiredArgsConstructor
public class MyPasswordController {

    private final MyPasswordService myPasswordService;

    @PostMapping
    public Map<String, String> changePassword(
            @Valid @RequestBody MyPasswordChangeRequest input,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (authentication == null
                || !(authentication.getPrincipal()
                        instanceof SqlFlowUserDetails user)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        // 서비스의 트랜잭션이 성공한 뒤 현재 세션을 종료한다.
        myPasswordService.changePassword(user.getUserId(), input);

        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);

        return Map.of(
                "message",
                "비밀번호가 변경되었습니다. 다시 로그인하세요.");
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
                .orElse("비밀번호 입력값을 확인하세요.");

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
                                "비밀번호를 변경하지 못했습니다.")));
    }
}