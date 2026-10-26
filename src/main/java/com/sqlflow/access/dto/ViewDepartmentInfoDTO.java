package com.sqlflow.access.dto;

import lombok.Getter;
import lombok.Setter;

/** 뷰 목록의 부서 필터에 사용할 부서 정보. */
@Getter
@Setter
public class ViewDepartmentInfoDTO {

    private Long deptId;
    private String deptName;
}