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
	
	private ApprovalCandidateResponse departmentApprover;
	private List<ApprovalCandidateResponse> dbApprovers;
}
