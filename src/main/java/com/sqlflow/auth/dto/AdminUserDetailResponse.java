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
    private final String employeeNo;

    public AdminUserDetailResponse(Long userId, String loginId, String userName,
            Long deptId, String deptName, Long positionId, String positionName,
            String enabled, List<String> roleCodes) {
        this(userId, loginId, userName, deptId, deptName, positionId, positionName, enabled, roleCodes, null);
    }
}
