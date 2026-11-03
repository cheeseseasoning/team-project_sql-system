package com.sqlflow.notice.controller;

import java.net.URI;
import java.security.Principal;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.notice.dto.NoticeCreateRequest;
import com.sqlflow.notice.service.NoticeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 시스템 관리자의 공지 등록 요청을 처리한다. */
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
public class AdminNoticeController {

    private final NoticeService noticeService;

    /** 등록한 공지 번호와 상세 API 주소를 반환한다. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createNotice(
            @Valid @RequestBody NoticeCreateRequest request,
            Principal principal) {

        String loginId = principal == null ? null : principal.getName();
        Long noticeId = noticeService.createNotice(request, loginId);

        return ResponseEntity.created(URI.create("notices/" + noticeId))
                .body(Map.of(
                        "noticeId", noticeId,
                        "message", "공지사항이 등록되었습니다."));
    }

    /** 서비스에서 발생한 오류의 상태 코드와 안내 메시지를 유지한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException exception) {

        String message = Objects.requireNonNullElse(exception.getReason(), "공지사항을 등록하지 못했습니다.");

        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }

    /** 제목·본문 입력 검증 실패를 화면에서 표시할 메시지로 변환한다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationError(MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Objects.requireNonNullElse(error.getDefaultMessage(), "입력 내용을 확인하세요."))
                .findFirst()
                .orElse("입력 내용을 확인하세요.");

        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}