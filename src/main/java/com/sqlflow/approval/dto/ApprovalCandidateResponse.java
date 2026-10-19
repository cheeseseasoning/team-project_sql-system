package com.sqlflow.approval.dto;

public record ApprovalCandidateResponse(
        Long userId,
        String userName,
        String deptName) {
}
