package com.sqlflow.auth.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.dto.AccountListItemResponse;
import com.sqlflow.auth.dto.AdminUserDetailResponse;
import com.sqlflow.auth.dto.AdminUserUpdateRequest;
import com.sqlflow.auth.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountService {
	
	private static final String INITIAL_PASSWORD = "kosa1004";
	
	private static final List<String> ALLOWED_ROLES = List.of(
			"SYSTEM_ADMIN",
			"DB_ADMIN",
			"DB_APPROVER",
			"SENSITIVE_DATA_READER");
	
	private final UserAccountMapper userAccountMapper;
	private final PasswordEncoder passwordEncoder;
	
	@Transactional(readOnly = true)
	public List<AccountListItemResponse> getAccountList(){
		return userAccountMapper.selectAllAccountListItems();
	}
	
	@Transactional(readOnly = true)
	public AdminUserDetailResponse getAccountDetail(
			String actorLoginId, 
			Long userId) {
		requireActiveSystemAdmin(actorLoginId);
		validateUserId(userId);
		
		UserAccount account = userAccountMapper.selectAccountDetailById(userId);
		
		if(account == null) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"계정을 찾을 수 없습니다.");
		}
		
		List<String> roleCodes = userAccountMapper.selectRoleCodes(userId);
		
		return new AdminUserDetailResponse(
				account.getUserId(),
				account.getLoginId(),
				account.getUserName(),
				account.getDeptId(),
				account.getDeptName(),
				account.getPositionId(),
				account.getPositionName(),
				account.getEnabled(),
				roleCodes);
	}
	
	@Transactional
	public void updateAccount(String actorLoginId, Long userId, AdminUserUpdateRequest request) {
		requireActiveSystemAdmin(actorLoginId);
		validateUserId(userId);
		
		if(request == null) {
			throw badRequest("계정 정보를 확인하세요.");
		}
		
		if(userAccountMapper.countByUserId(userId) == 0) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"계정을 찾을 수 없습니다");
		}
		
		String userName = trim(request.getUserName());
		
		if(userName == null 
				|| userName.isBlank() 
				|| userName.length() > 100) {
			throw badRequest("이름을 입력하세요. 이름은 100자 이하여야 합니다.");
		}
		
		if(request.getDeptId() == null 
				|| userAccountMapper.countDepartment(request.getDeptId()) == 0) {
			throw badRequest("유효한 부서를 선택하세요.");
		}
		
		if(request.getPositionId() == null
				|| userAccountMapper.countPosition(request.getPositionId()) == 0) {
			throw badRequest("유효한 직급을 선택하세요.");
		}
		
		String enabled = trim(request.getEnabled());
		
		if(!"Y".equals(enabled) && !"N".equals(enabled)) {
			throw badRequest("계정 사용 여부는 Y 또는 N이어야 합니다.");
		}
		
		List<String> roleCodes = request.getRoles() == null
				? List.of()
				: request.getRoles().stream()  
						 .filter(code -> code != null && !code.isBlank())
						 .map(String::trim)
						 .distinct()
						 .toList();
		
		if(!ALLOWED_ROLES.containsAll(roleCodes)) {
			throw badRequest("지원하지 않는 역할이 포함되어 있습니다.");
		}
		
		List<Long> roleIds = roleCodes.isEmpty()
				? List.of() 
				: userAccountMapper.selectRoleIdsByCodes(roleCodes);
		
		if(roleIds.size() != roleCodes.size()) {
			throw badRequest("역할 정보를 확인할 수 없습니다.");
		}
		
		int updated = userAccountMapper.updateAccountInfo(
				userId, 
				userName, 
				request.getDeptId(),
				request.getPositionId(),
				enabled);
		
		if(updated != 1) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT,
					"계정 정보를 수정하지 못했습니다. 계정 상태를 다시 확인하세요.");
		}
		
		userAccountMapper.deleteUserRoles(userId);
		
		for(Long roleId : roleIds) {
			userAccountMapper.insertUserRole(userId, roleId);
		}
	}
	
	@Transactional
	public void resetInitialPassword(
			String actorLoginId, 
			Long userId) {
		requireActiveSystemAdmin(actorLoginId);
		validateUserId(userId);
		
		if(userAccountMapper.countByUserId(userId) == 0) {
			throw new ResponseStatusException(
					HttpStatus.NOT_FOUND,
					"계정을 찾을 수 없습니다.");
		}
		
		String passwordHash = passwordEncoder.encode(INITIAL_PASSWORD);
		int updated = userAccountMapper.updatePasswordHash(userId, passwordHash);
		
		if(updated != 1) {
			throw new ResponseStatusException(
					HttpStatus.CONFLICT, 
					"초기 비밀번호를 재설정하지 못했습니다.");
		}
	}
	
	@Transactional(readOnly = true)
	public void assertActiveSystemAdmin(String loginId) {
		requireActiveSystemAdmin(loginId);
	}
	
	private void requireActiveSystemAdmin(String loginId) {
		if (loginId == null || loginId.isBlank()) {
			throw new ResponseStatusException(
					HttpStatus.UNAUTHORIZED,
					"로그인이 필요합니다.");
		}
		
		if (userAccountMapper.countActiveSystemAdminByLoginId(loginId) != 1) {
			throw new ResponseStatusException(
					HttpStatus.FORBIDDEN, 
					"활성 시스템 관리자만 계정을 관리할 수 있습니다.");
		}
	}
	
	private void validateUserId(Long userId) {
		if (userId == null || userId <= 0) {
			throw badRequest("계정 ID가 올바르지 않습니다.");
		}
	}
	
	private String trim(String value) {
		return value == null ? null : value.trim();
	}
	
	private ResponseStatusException badRequest(String message) {
		return new ResponseStatusException(
				HttpStatus.BAD_REQUEST, 
				message);
	}
}















