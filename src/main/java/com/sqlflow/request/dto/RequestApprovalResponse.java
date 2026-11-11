package com.sqlflow.request.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestApprovalResponse {

    private Long approvalId;
    private Integer approvalOrder;
    private String approvalType;
    private String status;

    private Long approverId;
    private String approverName;
    private String deptName;
    private String positionName;

    private LocalDateTime decidedAt;
}