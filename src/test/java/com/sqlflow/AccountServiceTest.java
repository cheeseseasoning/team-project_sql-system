package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.dto.AdminUserUpdateRequest;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.auth.service.AccountService;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AccountService accountService;

    @Test
    void updatesAccountFieldsAndReplacesRoles() {
        Long userId = 25L;
        List<String> roleCodes = List.of("DB_ADMIN", "DB_APPROVER");
        List<Long> roleIds = List.of(4L, 7L);
        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "  준오  ", 2L, 3L, "Y", roleCodes);

        when(userAccountMapper.countByUserId(userId)).thenReturn(1);
        when(userAccountMapper.countDepartment(2L)).thenReturn(1);
        when(userAccountMapper.countPosition(3L)).thenReturn(1);
        when(userAccountMapper.selectRoleIdsByCodes(roleCodes)).thenReturn(roleIds);
        when(userAccountMapper.updateAccountInfo(
                userId, "준오", 2L, 3L, "Y")).thenReturn(1);

        accountService.updateAccount(userId, request);

        verify(userAccountMapper).updateAccountInfo(
                userId, "준오", 2L, 3L, "Y");
        verify(userAccountMapper).deleteUserRoles(userId);
        verify(userAccountMapper).insertUserRole(userId, 4L);
        verify(userAccountMapper).insertUserRole(userId, 7L);
    }

    @Test
    void rejectsBlankNameWithoutUpdatingAccount() {
        Long userId = 25L;
        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "  ", 2L, 3L, "Y", List.of());
        when(userAccountMapper.countByUserId(userId)).thenReturn(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> accountService.updateAccount(userId, request));

        assertEquals(400, exception.getStatusCode().value());
        verify(userAccountMapper, never()).updateAccountInfo(
                eq(userId), eq(""), eq(2L), eq(3L), eq("Y"));
        verify(userAccountMapper, never()).deleteUserRoles(userId);
    }

    @Test
    void rejectsUnknownRoleWithoutUpdatingAccount() {
        Long userId = 25L;
        AdminUserUpdateRequest request = new AdminUserUpdateRequest(
                "준오", 2L, 3L, "Y", List.of("UNRECOGNIZED_ROLE"));
        when(userAccountMapper.countByUserId(userId)).thenReturn(1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> accountService.updateAccount(userId, request));

        assertEquals(400, exception.getStatusCode().value());
        verify(userAccountMapper, never()).selectRoleIdsByCodes(anyList());
        verify(userAccountMapper, never()).updateAccountInfo(
                eq(userId), eq("준오"), eq(2L), eq(3L), eq("Y"));
    }

    @Test
    void resetsExistingAccountPasswordToEncodedInitialPassword() {
        Long userId = 25L;
        when(userAccountMapper.countByUserId(userId)).thenReturn(1);
        when(passwordEncoder.encode("kosa1004")).thenReturn("bcrypt-hash");
        when(userAccountMapper.updatePasswordHash(userId, "bcrypt-hash"))
                .thenReturn(1);

        accountService.resetInitialPassword(userId);

        verify(passwordEncoder).encode("kosa1004");
        verify(userAccountMapper).updatePasswordHash(userId, "bcrypt-hash");
    }

    @Test
    void doesNotResetPasswordWhenAccountDoesNotExist() {
        Long userId = 999L;
        when(userAccountMapper.countByUserId(userId)).thenReturn(0);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> accountService.resetInitialPassword(userId));

        assertEquals(404, exception.getStatusCode().value());
        verifyNoInteractions(passwordEncoder);
        verify(userAccountMapper, never()).updatePasswordHash(eq(userId), anyString());
    }
}
