package com.sqlflow.access.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 리소스에 연결된 부서별 정책.
 * 정책이 없는 부서는 policyId와 정책 필드가 null이다.
 */
@Getter
@Setter
public class ViewDepartmentPolicyInfoDTO {

    private Long resourceId;
    private Long deptId;
    private String deptName;

    private Long policyId;
    private String accessLevel;
    private String exportPolicy;
    private String policyEnabled;
}