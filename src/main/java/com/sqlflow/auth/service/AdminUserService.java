package com.sqlflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

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

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Map<String, Object> getOptions() {
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
    public Map<String, Object> create(AdminUserCreateRequest request) {
        if (request == null) {
            throw badRequest("계정 정보를 확인하세요.");
        }

        String loginId = trim(request.getLoginId());
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
                        .distinct()
                        .toList();

        if (roleCodes.isEmpty()) {
            throw badRequest("하나 이상의 업무 역할을 선택하세요.");
        }

        List<String> allowedRoles = List.of(
                "SYSTEM_ADMIN",
                "DB_ADMIN",
                "DB_APPROVER",
                "SENSITIVE_DATA_READER");

        if (!allowedRoles.containsAll(roleCodes)) {
            throw badRequest("지원하지 않는 역할이 포함되어 있습니다.");
        }

        if (userAccountMapper.countByLoginId(loginId) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 사용 중인 아이디입니다.");
        }

        List<Long> roleIds =
                userAccountMapper.selectRoleIdsByCodes(roleCodes);

        if (roleIds.size() != roleCodes.size()) {
            throw badRequest("역할 정보를 확인할 수 없습니다.");
        }

        Long userId = userAccountMapper.nextUserId();
        String passwordHash = passwordEncoder.encode(password);

        userAccountMapper.insertUser(
                userId,
                request.getDeptId(),
                request.getPositionId(),
                loginId,
                passwordHash,
                userName);

        for (Long roleId : roleIds) {
            userAccountMapper.insertUserRole(userId, roleId);
        }

        return Map.of(
                "userId", userId,
                "loginId", loginId,
                "userName", userName,
                "message", "계정이 생성되었습니다.");
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