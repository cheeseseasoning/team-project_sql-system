package com.sqlflow.access.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.access.service.ResourcePolicyAdminService;
import com.sqlflow.access.service.ResourcePolicyAdminService.PolicyRequest;
import com.sqlflow.access.service.ResourcePolicyAdminService.ResourceRequest;
import com.sqlflow.access.service.ResourcePolicyAdminService.DepartmentRequest;
import com.sqlflow.auth.security.SqlFlowUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/db-admin/resources")
@RequiredArgsConstructor
public class ResourcePolicyAdminController {
    private final ResourcePolicyAdminService service;

    @GetMapping
    public List<Map<String, Object>> list(Authentication authentication) {
        return service.list(userId(authentication));
    }

    @GetMapping("/departments")
    public List<ViewDepartmentInfoDTO> departments(Authentication authentication) {
        return service.departments(userId(authentication));
    }

    @PostMapping
    public Map<String, Object> register(Authentication authentication, @RequestBody ResourceRequest request) {
        return service.register(userId(authentication), request);
    }

    @PutMapping("/{resourceId}/policies")
    public Map<String, Object> savePolicy(Authentication authentication, @PathVariable Long resourceId,
            @RequestBody PolicyRequest request) {
        return service.savePolicy(userId(authentication), resourceId, request);
    }

    @PutMapping("/{resourceId}/departments")
    public Map<String, Object> saveDepartments(Authentication authentication, @PathVariable Long resourceId,
            @RequestBody DepartmentRequest request) {
        return service.saveDepartments(userId(authentication), resourceId, request);
    }

    private Long userId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SqlFlowUserDetails user) return user.getUserId();
        return null;
    }
}
