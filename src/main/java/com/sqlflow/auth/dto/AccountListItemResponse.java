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
    private final String employeeNo;

    // 사번을 사용하지 않는 기존 호출부도 유지한다.
    public AccountListItemResponse(Long userId, String loginId, String userName,
            String deptName, String positionName, String roleNames, String enabled) {
        this(userId, loginId, userName, deptName, positionName, roleNames, enabled, null);
    }
}
