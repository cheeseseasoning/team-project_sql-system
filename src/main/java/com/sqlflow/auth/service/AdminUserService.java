package com.sqlflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.AdminUserCreateRequest;
import com.sqlflow.auth.mapper.UserAccountMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {
	
	private static final String DUPLICATE_LOGIN_ID_MESSAGE = 
			"로그인 ID가 이미 사용 중입니다. 다른 ID를 입력해 주세요.";

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Map<String, Object> getOptions(String actorLoginId) {
    	requireActiveSystemAdmin(actorLoginId);
    	
        return Map.of(
                "departments", userAccountMapper.selectDepartments(),
                "positions", userAccountMapper.selectPositions(),
                "roles", List.of(
                        "SYSTEM_ADMIN",
                        "DB_ADMIN",
                        "DB_APPROVER",
                        "SENSITIVE_DATA_READER"));
    }

    @Transactional
    public Map<String, Object> create(
    		String actorLoginId, 
    		AdminUserCreateRequest request) {
    	requireActiveSystemAdmin(actorLoginId);
    	
        if (request == null) {
            throw badRequest("계정 정보를 확인하세요.");
        }

        String loginId = trim(request.getLoginId());
        String employeeNo = trim(request.getEmployeeNo());
        
        if (employeeNo != null && employeeNo.isEmpty()) {
            employeeNo = null;
        }
        
        if (employeeNo == null || !employeeNo.matches("[0-9]{4}-[0-9]{5}")) {
            throw badRequest("사번은 2026-00042 형식으로 입력하세요.");
        }
        
        String userName = trim(request.getUserName());
        String password = request.getInitialPassword();

        if (loginId == null || !loginId.matches("[A-Za-z0-9._-]{3,50}")) {
            throw badRequest("아이디는 영문·숫자·마침표·밑줄·하이픈으로 3~50자 입력하세요.");
        }
        if (userName == null || userName.length() > 100) {
            throw badRequest("이름을 입력하세요. 이름은 100자 이하여야 합니다.");
        }
        if (request.getDeptId() == null
                || userAccountMapper.countDepartment(request.getDeptId()) == 0) {
            throw badRequest("유효한 부서를 선택하세요.");
        }
        if (request.getPositionId() == null
                || userAccountMapper.countPosition(request.getPositionId()) == 0) {
            throw badRequest("유효한 직급을 선택하세요.");
        }
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw badRequest("비밀번호는 8자 이상이어야 하며 UTF-8 기준 72바이트 이하여야 합니다.");
        }

        List<String> roleCodes = request.getRoles() == null
                ? List.of()
                : request.getRoles().stream()
                        .filter(code -> code != null && !code.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();

        List<String> allowedRoles = List.of(
                "SYSTEM_ADMIN",
                "DB_ADMIN",
                "DB_APPROVER",
                "SENSITIVE_DATA_READER");

        if (!allowedRoles.containsAll(roleCodes)) {
            throw badRequest("지원하지 않는 역할이 포함되어 있습니다.");
        }

        if (userAccountMapper.countByLoginId(loginId) > 0) {
            throw duplicateLoginId();
        }

        List<Long> roleIds = roleCodes.isEmpty()
                ? List.of()
                : userAccountMapper.selectRoleIdsByCodes(roleCodes);

        if (roleIds.size() != roleCodes.size()) {
            throw badRequest("역할 정보를 확인할 수 없습니다.");
        }

        Long userId = userAccountMapper.nextUserId();
        String passwordHash = passwordEncoder.encode(password);
        
        try {
        	
	        userAccountMapper.insertUser(
	                userId,
	                request.getDeptId(),
	                request.getPositionId(),
	                loginId,
	                passwordHash,
	                userName,
	                employeeNo);
        
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception, "UK_USERS_EMPLOYEE_NO")) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "이미 등록된 사번입니다.");
            }

            if (isUniqueViolation(exception, "UX_USERS_LOGIN_ID")) {
                throw duplicateLoginId();
            }

            throw exception;
        }

        for (Long roleId : roleIds) {
            userAccountMapper.insertUserRole(userId, roleId);
        }

        return Map.of(
                "userId", userId,
                "loginId", loginId,
                "userName", userName,
                "message", "계정이 생성되었습니다.");
    }
    
    private void requireActiveSystemAdmin(String loginId) {
    	if(loginId == null || loginId.isBlank()) {
    		throw new ResponseStatusException(
    				HttpStatus.UNAUTHORIZED,
    				"로그인이 필요합니다.");
    	}
    	
    	if(userAccountMapper.countActiveSystemAdminByLoginId(loginId) != 1) {
    		throw new ResponseStatusException(
    				HttpStatus.FORBIDDEN, 
    				"활성 시스템 관리자만 계정을 관리할 수 있습니다.");
    	}
    }
    
    private boolean isUniqueViolation(Throwable exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1) {
                String message = sqlException.getMessage();
                if (message != null
                        && (message.contains("." + constraintName + ")")
                            || message.contains("(" + constraintName + ")"))) {
                    return true;
                }
            }
        }

        return false;
    }
    
    private ResponseStatusException duplicateLoginId() {
    	return new ResponseStatusException(
    			HttpStatus.CONFLICT,
    			DUPLICATE_LOGIN_ID_MESSAGE);
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
