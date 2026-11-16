package com.sqlflow.approval.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalCandidateResponse {
	
	private Long userId;
	private String userName;
	private String deptName;
	private String positionName;
	private Long deptId;

	public ApprovalCandidateResponse(Long userId, String userName, String deptName, String positionName) {
		this(userId, userName, deptName, positionName, null);
	}
}