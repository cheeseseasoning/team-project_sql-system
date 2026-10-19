package com.sqlflow.approval.dto;

import java.util.List;

public record ApprovalCandidatesResponse(
        ApprovalCandidateResponse departmentApprover,
        List<ApprovalCandidateResponse> dbApprovers) {
}
