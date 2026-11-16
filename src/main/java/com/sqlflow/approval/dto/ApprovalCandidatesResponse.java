package com.sqlflow.approval.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCandidatesResponse {

    private List<ApprovalCandidateResponse> candidates;
    private List<ApprovalDepartmentCandidateResponse> departments = List.of();

    // 기존 개인 후보만 받던 호출 형식을 유지한다.
    public ApprovalCandidatesResponse(List<ApprovalCandidateResponse> candidates) {
        this(candidates, List.of());
    }
}