package com.sqlflow.auth.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AdminUserDetailResponse {
	
	private final Long userId;
	private final String loginId;
	private final String userName;
	private final Long deptId;
	private final String deptName;
	private final Long positionId;
	private final String positionName;
	private final String enabled;
	private final List<String> roleCodes;
}
