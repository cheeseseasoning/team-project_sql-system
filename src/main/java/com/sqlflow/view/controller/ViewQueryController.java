package com.sqlflow.view.controller;

import java.util.List;
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

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.view.dto.ViewDetailResponseDTO;
import com.sqlflow.view.dto.ViewSummaryResponseDTO;
import com.sqlflow.view.service.ViewQueryService;

import lombok.RequiredArgsConstructor;

/**
 * 읽기 전용 뷰 목록과 부서 필터 정보를 제공한다.
 * 로그인 여부는 기존 Spring Security 설정에서 확인한다.
 */
@RestController
@RequestMapping("/api/views")
@RequiredArgsConstructor
public class ViewQueryController {

    private final ViewQueryService viewQueryService;

    /** 부서와 검색어 조건에 맞는 실제 뷰 목록을 반환한다. */
    @GetMapping
    public List<ViewSummaryResponseDTO> getViews(
            @RequestParam(name = "deptId", required = false) Long deptId,
            @RequestParam(name = "keyword", defaultValue = "") String keyword) {

        return viewQueryService.getViews(deptId, keyword);
    }

    /** 부서 드롭다운에 사용할 전체 부서를 반환한다. */
    @GetMapping("/departments")
    public List<ViewDepartmentInfoDTO> getDepartments() {
        return viewQueryService.getDepartments();
    }
    
    /** 실제 뷰의 등록 정보·부서 정책·컬럼·CREATE문을 반환한다. */
    /** 로그인 사용자의 현재 소속에 맞는 정책 범위로 상세를 반환한다. */
    @GetMapping("/detail")
    public ViewDetailResponseDTO getViewDetail(
            @RequestParam(name = "schemaName") String schemaName,
            @RequestParam(name = "objectName") String objectName,
            @AuthenticationPrincipal SqlFlowUserDetails user) {

        return viewQueryService.getViewDetail(schemaName, objectName, user == null ? null : user.getUserId());
    }
    
    /**
     * 서비스에서 정의한 오류 안내를 JSON으로 반환한다.
     * 상태 코드는 유지하고, 화면에서 사용할 message를 전달한다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleError(ResponseStatusException exception) {

        String message = Objects.requireNonNullElse(exception.getReason(), "뷰 정보를 조회하지 못했습니다. 잠시 후 다시 시도해 주세요.");

        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", message));
    }
}