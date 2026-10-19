package com.sqlflow.approval.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.request.dto.RequestDetailResponse;

import java.util.List;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/pending")
    public List<PendingApprovalResponse> getPendingApprovals(
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return approvalService.getPendingApprovals(user.getUserId());
    }

    @GetMapping("/{requestId}")
    public RequestDetailResponse getPendingApprovalDetail(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        return approvalService.getPendingApprovalDetail(requestId, user.getUserId());
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable Long requestId,
            @AuthenticationPrincipal SqlFlowUserDetails user) {
        approvalService.approve(requestId, user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApprovalErrorResponse> handleApprovalError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                new ApprovalErrorResponse(exception.getReason()));
    }

    public record ApprovalErrorResponse(String message) {
    }
}
