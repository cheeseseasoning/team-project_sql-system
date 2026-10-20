package com.sqlflow.approval.dto;

import java.time.LocalDateTime;

public record PendingApprovalResponse(
        Long requestId,
        String requestNo,
        String title,
        String purpose,
        String drafterName,
        String deptName,
        LocalDateTime submittedAt,
        Integer approvalOrder,
        String approvalType) {
}
