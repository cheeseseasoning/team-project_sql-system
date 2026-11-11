package com.sqlflow.request.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentRequestListResponse extends RequestListResponse {

    private String drafterName;
    private String deptName;
    private LocalDateTime submittedAt;

    /**
     * 현재 결재 차례 유형.
     * 예: APPROVER, INTERMEDIATE, DIRECTOR, DB_EXECUTION
     */
    private String currentApprovalType;
}