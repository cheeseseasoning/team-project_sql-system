package com.sqlflow.auth.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAccount {

	// 식별 및 로그인 정보
	private Long userId;
	private String loginId;
	private String passwordHash;

	// 개인 정보
	private String employeeNo;
	private String userName;
	private String phoneNumber;
	private String email;

	// 소속 정보
	private Long deptId;
	private String deptName;
	private Long positionId;
	private String positionName;

	// 계정 상태
	private String enabled;
}
