package com.sqlflow.approval.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalDepartmentCandidateResponse {

    private Long deptId;
    private String deptName;

    // 활성 사용자 중 기안자를 제외한 결재 가능 인원.
    private Integer eligibleUserCount;
}