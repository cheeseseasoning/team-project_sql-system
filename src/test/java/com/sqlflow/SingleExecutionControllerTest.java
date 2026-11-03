package com.sqlflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SecurityConfig;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.controller.SingleExecutionController;
import com.sqlflow.execution.service.SingleExecutionDispatchService;
import com.sqlflow.execution.service.SingleExecutionRecordService.SingleStartResult;

/**
 * 실제 MVC와 Security 필터를 사용한다.
 * 실행 서비스는 Mock이므로 실제 SQL이나 DB 상태는 변경하지 않는다.
 */
@WebMvcTest(controllers = SingleExecutionController.class)
@Import(SecurityConfig.class)
class SingleExecutionControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    private static final Long REQUEST_ID = 100L;
    private static final Long QUERY_ID = 200L;
    private static final Long EXECUTION_ID = 400L;

    private static final String API_URL =
            "/api/executions/requests/{requestId}/queries/{queryId}/run";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private SingleExecutionDispatchService dispatchService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void 로그인하지_않으면_401을_반환한다() throws Exception {
        // 인증 검사만 확인하기 위해 CSRF는 통과시킨다.
        mockMvc.perform(
                    post(API_URL, REQUEST_ID, QUERY_ID)
                            .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("로그인이 필요합니다."));

        verifyNoInteractions(dispatchService);
    }

    @Test
    void CSRF가_없으면_403을_반환한다() throws Exception {
        mockMvc.perform(
                    post(API_URL, REQUEST_ID, QUERY_ID)
                            .with(user(loginUser())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dispatchService);
    }

    @Test
    void 접수에_성공하면_202와_실행_식별자를_반환한다()
            throws Exception {
        SqlFlowUserDetails actor = loginUser();

        when(dispatchService.submit(
                eq(REQUEST_ID),
                eq(QUERY_ID),
                any(SqlFlowUserDetails.class)))
                .thenReturn(new SingleStartResult(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID));

        mockMvc.perform(
                    post(API_URL, REQUEST_ID, QUERY_ID)
                            .with(user(actor))
                            .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(100))
                .andExpect(jsonPath("$.queryId").value(200))
                .andExpect(jsonPath("$.executionId").value(400));

        // 로그인 사용자가 실행 서비스에 전달되는지 확인한다.
        verify(dispatchService).submit(
                REQUEST_ID, QUERY_ID, actor);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 409, 503, 500})
    void 서비스의_오류상태와_메시지를_응답으로_전달한다(
            int statusCode) throws Exception {
        when(dispatchService.submit(
                eq(REQUEST_ID),
                eq(QUERY_ID),
                any(SqlFlowUserDetails.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.valueOf(statusCode),
                        "테스트 오류"));

        mockMvc.perform(
                    post(API_URL, REQUEST_ID, QUERY_ID)
                            .with(user(loginUser()))
                            .with(csrf()))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message").value("테스트 오류"));
    }

    private SqlFlowUserDetails loginUser() {
        UserAccount account = new UserAccount();
        account.setUserId(300L);
        account.setLoginId("execution-api-test");
        account.setUserName("실행 API 테스트");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(
                account, List.of("DB_APPROVER"));
    }
}