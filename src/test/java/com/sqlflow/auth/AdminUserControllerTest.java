package com.sqlflow.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.controller.AdminUserController;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.dto.AdminUserCreateRequest;
import com.sqlflow.auth.dto.AdminUserDetailResponse;
import com.sqlflow.auth.dto.AdminUserUpdateRequest;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.AccountService;
import com.sqlflow.auth.service.AdminUserService;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock
    private AdminUserService adminUserService;

    @Mock
    private AccountService accountService;

    @InjectMocks
    private AdminUserController controller;

    @Test
    void optionsPassesCurrentLoginIdToService() {
        String actorLoginId = "system-admin";
        SqlFlowUserDetails currentUser = createUserDetails(actorLoginId);
        Map<String, Object> options = Map.of(
                "departments", List.of(),
                "positions", List.of(),
                "roles", List.of("SYSTEM_ADMIN"));

        when(adminUserService.getOptions(actorLoginId))
                .thenReturn(options);

        Map<String, Object> response = controller.options(currentUser);

        verify(adminUserService).getOptions(actorLoginId);
        assertEquals(options, response);
    }

    @Test
    void detailPassesCurrentLoginIdToAccountService() {
        Long targetUserId = 25L;
        String actorLoginId = "system-admin";
        SqlFlowUserDetails currentUser = createUserDetails(actorLoginId);

        AdminUserDetailResponse expected = new AdminUserDetailResponse(
                targetUserId,
                "target-user",
                "대상 사용자",
                2L,
                "개발팀",
                3L,
                "사원",
                "Y",
                List.of());

        when(accountService.getAccountDetail(
                actorLoginId,
                targetUserId)).thenReturn(expected);

        AdminUserDetailResponse response =
                controller.detail(targetUserId, currentUser);

        verify(accountService).getAccountDetail(
                actorLoginId,
                targetUserId);
        assertEquals(expected, response);
    }

    @Test
    void createPassesCurrentLoginIdAndRequestToService() {
        String actorLoginId = "system-admin";
        SqlFlowUserDetails currentUser = createUserDetails(actorLoginId);

        AdminUserCreateRequest request = new AdminUserCreateRequest();
        Map<String, Object> result = Map.of(
                "userId", 100L,
                "loginId", "new-user",
                "message", "계정이 생성되었습니다.");

        when(adminUserService.create(actorLoginId, request))
                .thenReturn(result);

        ResponseEntity<Map<String, Object>> response =
                controller.create(request, currentUser);

        verify(adminUserService).create(actorLoginId, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(result, response.getBody());
    }

    @Test
    void updatePassesCurrentLoginIdAndRequestToAccountService() {
        Long targetUserId = 25L;
        String actorLoginId = "system-admin";
        SqlFlowUserDetails currentUser = createUserDetails(actorLoginId);

        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "테스트 사용자",
                2L,
                3L,
                "Y",
                List.of("DB_APPROVER"));

        ResponseEntity<Map<String, String>> response =
                controller.update(targetUserId, request, currentUser);

        verify(accountService).updateAccount(
                actorLoginId,
                targetUserId,
                request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "계정 정보가 수정되었습니다.",
                response.getBody().get("message"));
    }

    @Test
    void resetPasswordPassesCurrentLoginIdToAccountService() {
        Long targetUserId = 25L;
        String actorLoginId = "system-admin";
        SqlFlowUserDetails currentUser = createUserDetails(actorLoginId);

        ResponseEntity<Map<String, String>> response =
                controller.resetPassword(targetUserId, currentUser);

        verify(accountService).resetInitialPassword(
                actorLoginId,
                targetUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(
                "초기 비밀번호가 kosa1004로 재설정되었습니다.",
                response.getBody().get("message"));
    }

    @Test
    void exceptionHandlerReturnsStatusAndMessage() {
        String message =
                "활성 시스템 관리자만 계정을 관리할 수 있습니다.";
        ResponseStatusException exception = new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                message);

        ResponseEntity<Map<String, String>> response =
                controller.handleResponseStatusException(exception);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(message, response.getBody().get("message"));
    }

    private SqlFlowUserDetails createUserDetails(String loginId) {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setLoginId(loginId);
        account.setPasswordHash("test-hash");
        account.setUserName("시스템 관리자");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(
                account,
                List.of("SYSTEM_ADMIN"));
    }
}