package com.sqlflow.auth.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAccount {

    private Long userId;
    private Long deptId;
    private String loginId;
    private String passwordHash;
    private String userName;
    private String enabled;
    private String deptName;
    private String positionName;
}
