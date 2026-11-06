package com.sqlflow.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AccountListItemResponse {
	
	private final Long userId;
	private final String loginId;
	private final String userName;
	private final String deptName;
	private final String positionName;
	private final String roleNames;
	private final String enabled;
}
