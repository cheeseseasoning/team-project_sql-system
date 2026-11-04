package com.sqlflow.auth.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;

@Getter
public class AdminUserUpdateRequest {
	
	private final String userName;
	private final Long deptId;
	private final Long positionId;
	private final String enabled;
	private final List<String> roles;
	
	@JsonCreator
	public AdminUserUpdateRequest(
			@JsonProperty("userName") String userName,
			@JsonProperty("deptId") Long deptId,
			@JsonProperty("positionId") Long positionId,
			@JsonProperty("enabled") String enabled,
			@JsonProperty("roles") List<String> roles) {
		this.userName = userName;
		this.deptId = deptId;
		this.positionId = positionId;
		this.enabled = enabled;
		this.roles = roles == null ? List.of() : List.copyOf(roles); 
	}
}
