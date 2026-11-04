package com.sqlflow.auth.controller;

import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.AdminUserCreateRequest;
import com.sqlflow.auth.dto.AdminUserDetailResponse;
import com.sqlflow.auth.dto.AdminUserUpdateRequest;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.AccountService;
import com.sqlflow.auth.service.AdminUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final AccountService accountService;

    @GetMapping("/options")
    public Map<String, Object> options(
    		@AuthenticationPrincipal SqlFlowUserDetails currentUser) {
        return adminUserService.getOptions(loginId(currentUser));
    }

    @GetMapping("/{userId}")
    public AdminUserDetailResponse detail(
            @PathVariable Long userId,
            @AuthenticationPrincipal SqlFlowUserDetails currentUser) {
        return accountService.getAccountDetail(
        		loginId(currentUser),
        		userId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody AdminUserCreateRequest request,
            @AuthenticationPrincipal SqlFlowUserDetails currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.create(
                		loginId(currentUser),
                		request));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long userId,
            @RequestBody AdminUserUpdateRequest request, 
            @AuthenticationPrincipal SqlFlowUserDetails currentUser) {
    	
        accountService.updateAccount(
        		loginId(currentUser),
        		userId, 
        		request);

        return ResponseEntity.ok(Map.of(
                "message", "계정 정보가 수정되었습니다."));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long userId,
            @AuthenticationPrincipal SqlFlowUserDetails currentUser) {
    	
        accountService.resetInitialPassword(
        		loginId(currentUser),
        		userId);

        return ResponseEntity.ok(Map.of(
                "message", "초기 비밀번호가 kosa1004로 재설정되었습니다."));
    }
    
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(
    		ResponseStatusException exception) {
    	
    	String message = Objects.requireNonNullElse(
    			exception.getReason(),
    			"요청을 처리할 수 없습니다.");
    	
    	return ResponseEntity.status(exception.getStatusCode())
    			.body(Map.of("message", message));
    }
    
    private String loginId(SqlFlowUserDetails currentUser) {
    	return currentUser == null ? null : currentUser.getLoginId();
    }
}








