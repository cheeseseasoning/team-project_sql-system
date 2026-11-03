package com.sqlflow.notice.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.notice.domain.Notice;
import com.sqlflow.notice.service.NoticeService;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 사용자에게 공지 목록과 상세를 제공한다.
 * 로그인 여부는 기존 Spring Security 설정에서 확인한다.
 */
@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    /** 공지 목록과 페이징에 필요한 정보를 반환한다. */
    @GetMapping
    public Map<String, Object> getNotices(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {

        List<Notice> notices = noticeService.getNoticePage(page, size);
        long totalCount = noticeService.countNotices();

        // 나머지가 있으면 마지막 페이지가 하나 더 필요하다.
        long totalPages = totalCount / size + (totalCount % size == 0 ? 0 : 1);

        return Map.of(
                "notices", notices,
                "page", page,
                "size", size,
                "totalCount", totalCount,
                "totalPages", totalPages);
    }

    /** 공지 번호에 해당하는 본문과 작성 정보를 반환한다. */
    @GetMapping("/{noticeId}")
    public Notice getNotice(@PathVariable(name = "noticeId") Long noticeId) {
        return noticeService.getNotice(noticeId);
    }

    /** 서비스에서 정한 상태 코드와 오류 메시지를 화면에 전달한다. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException exception) {

        String message = Objects.requireNonNullElse(exception.getReason(), "공지사항을 조회하지 못했습니다.");

        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }
}