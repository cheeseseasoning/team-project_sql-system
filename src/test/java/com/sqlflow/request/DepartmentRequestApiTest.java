package com.sqlflow.request;

import static com.sqlflow.request.RequestTestUsers.authenticatedUser;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.auth.security.SecurityConfig;
import com.sqlflow.request.controller.RequestController;
import com.sqlflow.request.dto.DepartmentRequestPageResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.service.DepartmentRequestService;
import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService.SqlType;
import com.sqlflow.request.service.RequestService;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.springframework.http.MediaType;

import com.sqlflow.request.service.DmlSqlValidationService.Status;
import com.sqlflow.request.service.DmlSqlValidationService.ValidationResult;

@WebMvcTest(controllers = RequestController.class)
@Import(SecurityConfig.class)
class DepartmentRequestApiTest {
	
    @MockitoBean
    private DmlSqlValidationService dmlSqlValidationService;

    private static final String URL = "/api/requests/department/page";

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebSecurity
    static class TestSecurityBootstrap {
    }

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private RequestService requestService;

    @MockitoBean
    private ApprovalService approvalService;

    @MockitoBean
    private DepartmentRequestService departmentRequestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(departmentRequestService);
    }

    @Test
    void defaultsUseAuthenticatedUserId() throws Exception {
        when(departmentRequestService.getRequestsPage(
                1L, "", "", 1, 10))
                .thenReturn(new DepartmentRequestPageResponse(
                        List.of(), 1, 10, 0, 0));

        mockMvc.perform(get(URL).with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.items").isEmpty());

        verify(departmentRequestService).getRequestsPage(
                1L, "", "", 1, 10);
    }

    @Test
    void callerCannotReplaceActorIdOrScope() throws Exception {
        when(departmentRequestService.getRequestsPage(
                1L, "매출", "READY", 2, 5))
                .thenReturn(new DepartmentRequestPageResponse(
                        List.of(), 2, 5, 0, 0));

        mockMvc.perform(get(URL)
                        .with(authenticatedUser())
                        .param("keyword", "매출")
                        .param("status", "READY")
                        .param("page", "2")
                        .param("size", "5")
                        .param("actorId", "999")
                        .param("deptId", "999")
                        .param("all", "true"))
                .andExpect(status().isOk());

        verify(departmentRequestService).getRequestsPage(
                1L, "매출", "READY", 2, 5);
    }

    @Test
    void invalidPageFormatIsRejected() throws Exception {
        mockMvc.perform(get(URL)
                        .with(authenticatedUser())
                        .param("page", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(departmentRequestService);
    }
    
    @Test
    void anonymousDetailRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/requests/department/100"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(departmentRequestService);
    }

    @Test
    void detailUsesAuthenticatedActorId() throws Exception {
        var detail = new RequestDetailResponse();
        detail.setRequestId(100L);
        detail.setTitle("부서 공유 기안");

        when(departmentRequestService.getRequestDetail(100L, 1L))
                .thenReturn(detail);

        mockMvc.perform(get("/api/requests/department/100")
                        .with(authenticatedUser())
                        .param("actorId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(100))
                .andExpect(jsonPath("$.title").value("부서 공유 기안"));

        verify(departmentRequestService).getRequestDetail(100L, 1L);
    }

    @Test
    void inaccessibleDetailReturnsNotFound() throws Exception {
        when(departmentRequestService.getRequestDetail(100L, 1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "조회 요청을 찾을 수 없습니다."));

        mockMvc.perform(get("/api/requests/department/100")
                        .with(authenticatedUser()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("조회 요청을 찾을 수 없습니다."));
    }
    
    @Test
    void anonymousHistoryRequestIsRejected() throws Exception {
        mockMvc.perform(get(
                        "/api/requests/department/100/approval-history"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(departmentRequestService);
    }

    @Test
    void historyUsesAuthenticatedActorId() throws Exception {
        var history = new ApprovalHistoryResponse();
        history.setApproverName("테스트 결재자");

        when(departmentRequestService.getRequestApprovalHistory(100L, 1L))
                .thenReturn(List.of(history));

        mockMvc.perform(get(
                        "/api/requests/department/100/approval-history")
                        .with(authenticatedUser())
                        .param("actorId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].approverName")
                        .value("테스트 결재자"));

        verify(departmentRequestService)
                .getRequestApprovalHistory(100L, 1L);
    }

    @Test
    void inaccessibleHistoryReturnsNotFound() throws Exception {
        when(departmentRequestService.getRequestApprovalHistory(100L, 1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "조회 요청을 찾을 수 없습니다."));

        mockMvc.perform(get(
                        "/api/requests/department/100/approval-history")
                        .with(authenticatedUser()))
                .andExpect(status().isNotFound());
    }
    
    @Test
    void dmlValidationReturnsResultForSubmittedSql() throws Exception {
        String sql = "DELETE FROM demo_product WHERE product_id = 1";

        when(dmlSqlValidationService.validate(SqlType.DELETE, sql))
                .thenReturn(new ValidationResult(Status.VALID, "기본 구문 검사 통과"));

        mockMvc.perform(post("/api/requests/dml-sql/validate")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sqlType":"DELETE","sqls":["DELETE FROM demo_product WHERE product_id = 1"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("VALID"))
                .andExpect(jsonPath("$[0].message").value("기본 구문 검사 통과"));

        verify(dmlSqlValidationService).validate(SqlType.DELETE, sql);
    }
}
