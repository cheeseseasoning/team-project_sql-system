package com.sqlflow.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

import com.sqlflow.auth.security.SqlFlowUserDetailsService;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthenticationTest {

    @Test
    void accountCreationPageRequiresLogin() throws Exception {
        mockMvc.perform(get("/admin/users/new"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void regularUserCannotAccessAccountCreationPage() throws Exception {
        mockMvc.perform(get("/admin/users/new").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                        .user("employee").roles("DB_ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void systemAdminCanAccessAccountCreationPage() throws Exception {
        // 관리자 페이지는 로그인 ID로 Oracle의 현재 관리자 권한을 다시 확인한다.
        List<String> loginIds = jdbcTemplate.queryForList("""
                SELECT U.LOGIN_ID
                FROM USERS U
                JOIN USER_ROLE UR ON UR.USER_ID = U.USER_ID
                JOIN ROLE R ON R.ROLE_ID = UR.ROLE_ID
                WHERE U.ENABLED = 'Y'
                  AND R.ROLE_CODE = 'SYSTEM_ADMIN'
                ORDER BY U.USER_ID
                FETCH FIRST 1 ROW ONLY
                """, String.class);

        assertFalse(loginIds.isEmpty(), "테스트 DB에 활성 SYSTEM_ADMIN 계정이 필요합니다.");

        // 실제 로그인과 같은 서비스로 프로젝트의 인증 사용자 객체를 만든다.
        var admin = userDetailsService.loadUserByUsername(loginIds.get(0));

        mockMvc.perform(get("/admin/users/new").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .view().name("admin-user-form"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SqlFlowUserDetailsService userDetailsService;

    @Test
    void loginPageIsPublicAndUnauthenticatedApiReturnsJson401() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/requests/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"message\":\"로그인이 필요합니다.\"}"));
    }

    @Test
    void invalidLoginRedirectsBackToLoginPage() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "account-that-does-not-exist")
                        .param("password", "invalid"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error"));
    }
}
