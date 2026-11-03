package com.sqlflow.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.auth.dto.AdminUserCreateRequest;
import com.sqlflow.auth.dto.AdminUserDetailResponse;
import com.sqlflow.auth.dto.AdminUserUpdateRequest;
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
    public Map<String, Object> options() {
        return adminUserService.getOptions();
    }

    @GetMapping("/{userId}")
    public AdminUserDetailResponse detail(
            @PathVariable Long userId) {
        return accountService.getAccountDetail(userId);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.create(request));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, String>> update(
            @PathVariable Long userId,
            @RequestBody AdminUserUpdateRequest request) {
        accountService.updateAccount(userId, request);

        return ResponseEntity.ok(Map.of(
                "message", "계정 정보가 수정되었습니다."));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @PathVariable Long userId) {
        accountService.resetInitialPassword(userId);

        return ResponseEntity.ok(Map.of(
                "message", "초기 비밀번호가 kosa1004로 재설정되었습니다."));
    }
}