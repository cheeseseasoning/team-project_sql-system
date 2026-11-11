package com.sqlflow.auth.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminUserCreateRequest {

	private String loginId;
	private String userName;
	private String employeeNo;
	private Long deptId;
	private Long positionId;
	private String initialPassword;
	private List<String> roles;
}
