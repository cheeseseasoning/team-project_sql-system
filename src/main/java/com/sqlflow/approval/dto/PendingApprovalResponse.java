package com.sqlflow.approval.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingApprovalResponse {
	
	private Long requestId;
	private String requestNo;
	private String requestType;
	private String title;
	private String purpose;
	private String drafterName;
	private String deptName;
	private LocalDateTime submittedAt;
	private Integer approvalOrder;
	private String approvalType;
}
