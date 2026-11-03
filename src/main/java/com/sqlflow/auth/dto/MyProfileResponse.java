package com.sqlflow.auth.dto;

import lombok.Getter;
import lombok.Setter;

/** 마이페이지에 표시할 본인 정보만 반환한다. */
@Getter
@Setter
public class MyProfileResponse {

    private String loginId;
    private String employeeNo;
    private String userName;

    private String deptName;
    private String positionName;

    private String phoneNumber;
    private String email;
}