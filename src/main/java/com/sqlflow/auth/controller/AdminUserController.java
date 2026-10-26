package com.sqlflow.auth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.auth.dto.AdminUserCreateRequest;
import com.sqlflow.auth.service.AdminUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping("/options")
    public Map<String, Object> options() {
        return adminUserService.getOptions();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody AdminUserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminUserService.create(request));
    }
}