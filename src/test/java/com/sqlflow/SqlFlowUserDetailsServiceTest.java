package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.security.SqlFlowUserDetailsService;

@ExtendWith(MockitoExtension.class)
class SqlFlowUserDetailsServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Test
    void loadsProfileRolesAndBcryptHashFromDatabaseAccount() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserAccount account = new UserAccount();
        account.setUserId(8L);
        account.setDeptId(3L);
        account.setLoginId("junoh");
        account.setPasswordHash(encoder.encode("correct horse"));
        account.setUserName("준오");
        account.setDeptName("DB팀");
        account.setPositionName("대리");
        account.setEnabled("Y");

        when(userAccountMapper.selectByLoginId("junoh")).thenReturn(account);
        when(userAccountMapper.selectRoleCodes(8L))
                .thenReturn(List.of("DB_APPROVER"));

        SqlFlowUserDetails user = (SqlFlowUserDetails)
                new SqlFlowUserDetailsService(userAccountMapper)
                        .loadUserByUsername("junoh");

        assertTrue(user.isEnabled());
        assertTrue(encoder.matches("correct horse", user.getPassword()));
        assertTrue(user.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority()
                        .equals("ROLE_DB_APPROVER")));
        verify(userAccountMapper).selectRoleCodes(8L);
    }

    @Test
    void disabledDatabaseAccountCannotAuthenticate() {
        UserAccount account = new UserAccount();
        account.setUserId(9L);
        account.setDeptId(2L);
        account.setLoginId("inactive");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("password"));
        account.setEnabled("N");
        when(userAccountMapper.selectByLoginId("inactive")).thenReturn(account);
        when(userAccountMapper.selectRoleCodes(9L)).thenReturn(List.of());

        SqlFlowUserDetails user = (SqlFlowUserDetails)
                new SqlFlowUserDetailsService(userAccountMapper)
                        .loadUserByUsername("inactive");

        assertFalse(user.isEnabled());
    }
}
