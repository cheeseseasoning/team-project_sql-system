package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sqlflow.auth.controller.MyProfileController;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.dto.MyProfileResponse;
import com.sqlflow.auth.dto.MyProfileUpdateRequest;
import com.sqlflow.auth.mapper.MyProfileMapper;
import com.sqlflow.auth.security.SecurityConfig;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.auth.service.MyProfileService;

@WebMvcTest(controllers = MyProfileController.class)
@Import({
    SecurityConfig.class,
    MyProfileService.class
})
class MyProfileControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    private static final Long USER_ID = 10L;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private MyProfileMapper myProfileMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 로그인하지_않으면_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(myProfileMapper);
    }

    @Test
    void 로그인한_본인의_정보만_필요한_항목으로_반환한다()
            throws Exception {
        when(myProfileMapper.selectProfileByUserId(USER_ID))
                .thenReturn(profile());

        mockMvc.perform(
                    get("/api/me")
                            .with(user(loginUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("my-account"))
                .andExpect(jsonPath("$.employeeNo").value("EMP001"))
                .andExpect(jsonPath("$.userName").value("홍길동"))
                .andExpect(jsonPath("$.deptName").value("개발팀"))
                .andExpect(jsonPath("$.positionName").value("사원"))
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.email").value("me@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.enabled").doesNotExist())
                .andExpect(jsonPath("$.roles").doesNotExist());

        verify(myProfileMapper).selectProfileByUserId(USER_ID);
    }

    @Test
    void 다른_사용자ID를_보내도_로그인_사용자만_조회한다()
            throws Exception {
        when(myProfileMapper.selectProfileByUserId(USER_ID))
                .thenReturn(profile());

        mockMvc.perform(
                    get("/api/me")
                            .param("userId", "999")
                            .with(user(loginUser())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("my-account"));

        verify(myProfileMapper).selectProfileByUserId(USER_ID);
        verifyNoMoreInteractions(myProfileMapper);
    }

    @Test
    void 사용가능한_계정이_조회되지_않으면_401을_반환한다()
            throws Exception {
        when(myProfileMapper.selectProfileByUserId(USER_ID))
                .thenReturn(null);

        mockMvc.perform(
                    get("/api/me")
                            .with(user(loginUser())))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    void 로그인하지_않으면_수정할_수_없다() throws Exception {
        mockMvc.perform(
                    patch("/api/me")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com"}
                                    """))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(myProfileMapper);
    }

    @Test
    void CSRF가_없으면_수정할_수_없다() throws Exception {
        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com"}
                                    """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(myProfileMapper);
    }

    @Test
    void 연락처_두_항목을_수정하고_수정된_정보를_반환한다()
            throws Exception {
        MyProfileResponse updated = profile();
        updated.setPhoneNumber("021234567");
        updated.setEmail("new@example.com");

        prepareUpdate(updated);

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "phoneNumber":" 02-123-4567 ",
                                      "email":" new@example.com "
                                    }
                                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("021234567"))
                .andExpect(jsonPath("$.email").value("new@example.com"));

        var captor =
                ArgumentCaptor.forClass(MyProfileUpdateRequest.class);

        verify(myProfileMapper).updateContact(
                eq(USER_ID), captor.capture());

        var input = captor.getValue();

        assertTrue(input.isPhoneNumberProvided());
        assertTrue(input.isEmailProvided());
        assertEquals("021234567", input.getPhoneNumber());
        assertEquals("new@example.com", input.getEmail());
    }

    @Test
    void 이메일만_전달하면_전화번호는_수정대상에서_제외한다()
            throws Exception {
        MyProfileResponse updated = profile();
        updated.setEmail("new@example.com");

        prepareUpdate(updated);

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email":"new@example.com",
                                      "userId":999,
                                      "deptId":999,
                                      "enabled":"N"
                                    }
                                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value("01012345678"))
                .andExpect(jsonPath("$.email").value("new@example.com"));

        var captor =
                ArgumentCaptor.forClass(MyProfileUpdateRequest.class);

        // 클라이언트가 보낸 userId가 아닌 로그인 ID로 수정한다.
        verify(myProfileMapper).updateContact(
                eq(USER_ID), captor.capture());

        assertFalse(captor.getValue().isPhoneNumberProvided());
        assertTrue(captor.getValue().isEmailProvided());
    }

    @Test
    void 빈_이메일은_NULL로_정리하되_누락된_전화번호는_유지한다()
            throws Exception {
        MyProfileResponse updated = profile();
        updated.setEmail(null);

        prepareUpdate(updated);

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"   "}
                                    """))
                .andExpect(status().isOk());

        var captor =
                ArgumentCaptor.forClass(MyProfileUpdateRequest.class);

        verify(myProfileMapper).updateContact(
                eq(USER_ID), captor.capture());

        var input = captor.getValue();

        assertTrue(input.isEmailProvided());
        assertNull(input.getEmail());
        assertFalse(input.isPhoneNumberProvided());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"phoneNumber\":\"abc\"}",
        "{\"phoneNumber\":\"123456789012345678901\"}",
        "{\"email\":\"invalid-email\"}",
        "{\"phoneNumberProvided\":true,\"emailProvided\":true}"
    })
    void 수정항목이_없거나_잘못된_입력은_거절한다(
            String body) throws Exception {

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myProfileMapper);
    }

    @Test
    void 비활성화됐거나_존재하지_않는_계정은_수정하지_못한다()
            throws Exception {
        when(myProfileMapper.updateContact(
                eq(USER_ID), any(MyProfileUpdateRequest.class)))
                .thenReturn(0);

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"new@example.com"}
                                    """))
                .andExpect(status().isUnauthorized());
    }
    
    @ParameterizedTest
    @CsvSource({
        "010-1234-5678, 01012345678",
        "02 123 4567, 021234567",
        "02-1234-5678, 0212345678",
        "031-123-4567, 0311234567",
        "031-1234-5678, 03112345678",
        "070-1234-5678, 07012345678"
    })
    void 전화번호를_검증하고_숫자로_정리한다(
            String phoneNumber,
            String expected) throws Exception {

        MyProfileResponse updated = profile();
        updated.setPhoneNumber(expected);
        prepareUpdate(updated);

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"phoneNumber\":\""
                                    + phoneNumber + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phoneNumber").value(expected));

        var captor =
                ArgumentCaptor.forClass(MyProfileUpdateRequest.class);

        verify(myProfileMapper).updateContact(
                eq(USER_ID), captor.capture());

        assertEquals(expected, captor.getValue().getPhoneNumber());
        assertTrue(captor.getValue().isPhoneNumberProvided());
        assertFalse(captor.getValue().isEmailProvided());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1",
        "123",
        "0101234567",
        "0341234567",
        "010-ABCD-5678",
        "---"
    })
    void 지원하지_않는_전화번호_형식은_거절한다(
            String phoneNumber) throws Exception {

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                    "{\"phoneNumber\":\""
                                    + phoneNumber + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(myProfileMapper);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void 전화번호의_빈_입력과_NULL은_삭제로_처리한다(
            String phoneNumber) throws Exception {

        MyProfileResponse updated = profile();
        updated.setPhoneNumber(null);
        prepareUpdate(updated);

        String value = phoneNumber == null
                ? "null"
                : "\"" + phoneNumber + "\"";

        mockMvc.perform(
                    patch("/api/me")
                            .with(user(loginUser()))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phoneNumber\":" + value + "}"))
                .andExpect(status().isOk());

        var captor =
                ArgumentCaptor.forClass(MyProfileUpdateRequest.class);

        verify(myProfileMapper).updateContact(
                eq(USER_ID), captor.capture());

        assertTrue(captor.getValue().isPhoneNumberProvided());
        assertNull(captor.getValue().getPhoneNumber());
        assertFalse(captor.getValue().isEmailProvided());
    }

    private void prepareUpdate(MyProfileResponse updated) {
        when(myProfileMapper.updateContact(
                eq(USER_ID), any(MyProfileUpdateRequest.class)))
                .thenReturn(1);

        when(myProfileMapper.selectProfileByUserId(USER_ID))
                .thenReturn(updated);
    }

    private MyProfileResponse profile() {
        MyProfileResponse profile = new MyProfileResponse();
        profile.setLoginId("my-account");
        profile.setEmployeeNo("EMP001");
        profile.setUserName("홍길동");
        profile.setDeptName("개발팀");
        profile.setPositionName("사원");
        profile.setPhoneNumber("01012345678");
        profile.setEmail("me@example.com");
        return profile;
    }

    private SqlFlowUserDetails loginUser() {
        UserAccount account = new UserAccount();
        account.setUserId(USER_ID);
        account.setLoginId("my-account");
        account.setUserName("홍길동");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(account, List.of());
    }
}