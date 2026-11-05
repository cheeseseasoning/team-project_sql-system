package com.sqlflow.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sqlflow.auth.controller.MyPasswordController;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.MyPasswordMapper;
import com.sqlflow.auth.security.SecurityConfig;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.MyPasswordService;

@WebMvcTest(controllers = MyPasswordController.class)
@Import({
    SecurityConfig.class,
    MyPasswordService.class
})
class MyPasswordControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    private static final Long USER_ID = 10L;
    private static final String CURRENT = "OldPassword123!";
    private static final String NEXT = "NewPassword456!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private MyPasswordMapper myPasswordMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 로그인하지_않으면_변경할_수_없다() throws Exception {
        mockMvc.perform(
                    post("/api/me/password")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, NEXT, NEXT)))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(myPasswordMapper);
    }

    @Test
    void CSRF가_없으면_변경할_수_없다() throws Exception {
        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, NEXT, NEXT)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(myPasswordMapper);
    }

    @Test
    void 본인의_비밀번호를_암호화해서_변경하고_세션을_종료한다()
            throws Exception {

        prepareCurrentPassword();
        when(myPasswordMapper.updatePasswordHash(
                eq(USER_ID), anyString())).thenReturn(1);

        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(
                    post("/api/me/password")
                            .session(session)
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, NEXT, NEXT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message")
                        .value("비밀번호가 변경되었습니다. 다시 로그인하세요."));

        ArgumentCaptor<String> hash =
                ArgumentCaptor.forClass(String.class);

        verify(myPasswordMapper)
                .updatePasswordHash(eq(USER_ID), hash.capture());

        assertTrue(passwordEncoder.matches(NEXT, hash.getValue()));
        assertFalse(passwordEncoder.matches(CURRENT, hash.getValue()));
        assertTrue(session.isInvalid());
    }

    @Test
    void 현재_비밀번호가_틀리면_변경하지_않고_세션을_유지한다()
            throws Exception {

        prepareCurrentPassword();
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(
                    post("/api/me/password")
                            .session(session)
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("WrongPassword!", NEXT, NEXT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("현재 비밀번호가 일치하지 않습니다."));

        verify(myPasswordMapper, never())
                .updatePasswordHash(eq(USER_ID), anyString());

        assertFalse(session.isInvalid());
    }

    @Test
    void 현재와_같은_비밀번호로는_변경할_수_없다()
            throws Exception {

        prepareCurrentPassword();

        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, CURRENT, CURRENT)))
                .andExpect(status().isBadRequest());

        verify(myPasswordMapper, never())
                .updatePasswordHash(eq(USER_ID), anyString());
    }

    @Test
    void 새_비밀번호_확인이_다르면_변경하지_않는다()
            throws Exception {

        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, NEXT, "DifferentPassword!")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("새 비밀번호 확인이 일치하지 않습니다."));

        verifyNoInteractions(myPasswordMapper);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "        "})
    void 규칙에_맞지_않는_새_비밀번호는_거절한다(
            String newPassword) throws Exception {

        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, newPassword, newPassword)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myPasswordMapper);
    }

    @Test
    void 문자수가_짧아도_UTF8_72바이트를_초과하면_거절한다()
            throws Exception {

        String newPassword = "가".repeat(25);

        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, newPassword, newPassword)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myPasswordMapper);
    }

    @Test
    void 사용가능한_계정이_없으면_변경하지_않는다()
            throws Exception {

        when(myPasswordMapper.selectPasswordHashForUpdate(USER_ID))
                .thenReturn(null);

        mockMvc.perform(
                    post("/api/me/password")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(CURRENT, NEXT, NEXT)))
                .andExpect(status().isUnauthorized());

        verify(myPasswordMapper, never())
                .updatePasswordHash(eq(USER_ID), anyString());
    }

    private void prepareCurrentPassword() {
        when(myPasswordMapper.selectPasswordHashForUpdate(USER_ID))
                .thenReturn(passwordEncoder.encode(CURRENT));
    }

    private SqlFlowUserDetails loginUser() {
        UserAccount account = new UserAccount();
        account.setUserId(USER_ID);
        account.setDeptId(1L);
        account.setLoginId("my-account");
        account.setUserName("홍길동");
        account.setEnabled("Y");

        // 세션의 해시가 아닌 DB 값을 사용하는지도 확인한다.
        account.setPasswordHash("unused-session-hash");

        return new SqlFlowUserDetails(account, List.of());
    }

    private String body(
            String current,
            String next,
            String confirm) {

        // 이 테스트에서 사용하는 고정 문자열은 JSON 특수문자를 포함하지 않는다.
        return """
                {
                    "currentPassword": "%s",
                    "newPassword": "%s",
                    "newPasswordConfirm": "%s"
                }
                """.formatted(current, next, confirm);
    }
}