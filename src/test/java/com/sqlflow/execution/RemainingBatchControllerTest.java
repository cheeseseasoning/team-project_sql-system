package com.sqlflow.execution;

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
import com.sqlflow.execution.controller.ExecutionController;
import com.sqlflow.execution.service.ExecutionBatchDispatchService;
import com.sqlflow.execution.service.ExecutionBatchRecordService.BatchStartResult;
import com.sqlflow.execution.service.ExecutionBatchService;
import com.sqlflow.execution.service.ExecutionBatchStatusService;
import com.sqlflow.execution.service.ExecutionQueueService;

@WebMvcTest(controllers = ExecutionController.class)
@Import(SecurityConfig.class)
class RemainingBatchControllerTest {

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    private static final Long REQUEST_ID = 100L;
    private static final Long BATCH_ID = 500L;

    private static final String API_URL =
            "/api/executions/requests/{requestId}/run";

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private ExecutionBatchService batchService;

    @MockitoBean
    private ExecutionBatchDispatchService dispatchService;

    @MockitoBean
    private ExecutionBatchStatusService statusService;

    @MockitoBean
    private ExecutionQueueService queueService;

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
        mockMvc.perform(
                    post(API_URL, REQUEST_ID)
                            .with(csrf()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dispatchService);
    }

    @Test
    void CSRF가_없으면_403을_반환한다() throws Exception {
        mockMvc.perform(
                    post(API_URL, REQUEST_ID)
                            .with(user(loginUser())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dispatchService);
    }

    @Test
    void 전체실행_접수에_성공하면_202와_배치ID를_반환한다()
            throws Exception {
        SqlFlowUserDetails actor = loginUser();

        when(dispatchService.submitRemaining(
                eq(REQUEST_ID),
                any(SqlFlowUserDetails.class)))
                .thenReturn(new BatchStartResult(
                        BATCH_ID,
                        REQUEST_ID,
                        List.of(701L, 702L)));

        mockMvc.perform(
                    post(API_URL, REQUEST_ID)
                            .with(user(actor))
                            .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.batchId").value(500))
                .andExpect(jsonPath("$.message")
                        .value("미실행 SQL의 전체 실행을 접수했습니다."));

        verify(dispatchService).submitRemaining(REQUEST_ID, actor);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 409, 503, 500})
    void 서비스_오류상태와_메시지를_전달한다(
            int statusCode) throws Exception {
        when(dispatchService.submitRemaining(
                eq(REQUEST_ID),
                any(SqlFlowUserDetails.class)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.valueOf(statusCode),
                        "전체 실행 테스트 오류"));

        mockMvc.perform(
                    post(API_URL, REQUEST_ID)
                            .with(user(loginUser()))
                            .with(csrf()))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.message")
                        .value("전체 실행 테스트 오류"));
    }

    private SqlFlowUserDetails loginUser() {
        UserAccount account = new UserAccount();
        account.setUserId(300L);
        account.setLoginId("whole-execution-test");
        account.setUserName("전체 실행 테스트");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(
                account, List.of("DB_APPROVER"));
    }
}