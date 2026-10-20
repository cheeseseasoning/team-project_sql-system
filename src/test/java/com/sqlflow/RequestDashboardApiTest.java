package com.sqlflow;

import static com.sqlflow.RequestTestUsers.authenticatedUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestSummaryResponse;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestDashboardApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestMapper requestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void configureAuthenticatedMockMvc() {
        mockMvc = AuthenticatedMockMvc.forRequester(webApplicationContext);
    }

    @Test
    void summaryCountsRequestsByStatus() throws Exception {
        String beforeResponse = mockMvc.perform(
                    get("/api/requests/dashboard")
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Map<String, Number> before = JsonPath.read(
                beforeResponse, "$.summary");

        createDraft("초안 집계");

        Long pendingId = createDraft("결재 대기 집계");
        Long completedId = createDraft("완료 집계");

        // 집계 확인용 데이터만 상태 변경
        // 실제 결재·실행 과정을 검증하는 테스트는 아님
        jdbcTemplate.update(
                """
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'PENDING_APPROVAL'
                WHERE REQUEST_ID = ?
                """,
                pendingId
        );

        jdbcTemplate.update(
                """
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'COMPLETED'
                WHERE REQUEST_ID = ?
                """,
                completedId
        );

        mockMvc.perform(
                    get("/api/requests/dashboard")
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalCount")
                        .value(before.get("totalCount").longValue() + 3))
                .andExpect(jsonPath("$.summary.draftCount")
                        .value(before.get("draftCount").longValue() + 1))
                .andExpect(jsonPath("$.summary.pendingApprovalCount")
                        .value(before.get("pendingApprovalCount")
                                .longValue() + 1))
                .andExpect(jsonPath("$.summary.completedCount")
                        .value(before.get("completedCount")
                                .longValue() + 1));
    }

    @Test
    void recentRequestsContainLatestFiveRequests() throws Exception {
        List<Long> expectedIds = new ArrayList<>();

        for (int index = 1; index <= 7; index++) {
            expectedIds.add(createDraft("최근 요청 " + index));
        }

        Collections.reverse(expectedIds);

        String response = mockMvc.perform(
                    get("/api/requests/dashboard")
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recentRequests.length()").value(5))
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<Number> ids = JsonPath.read(
                response, "$.recentRequests[*].requestId");

        List<Long> actualIds = ids.stream()
                .map(Number::longValue)
                .toList();

        assertEquals(expectedIds.subList(0, 5), actualIds);
    }

    @Test
    void anotherDrafterHasNoSummaryOrRecentRequests() {
        createDraft("작성자 제한 확인");

        // 요청이 없는 작성자 조건으로 집계
        RequestSummaryResponse summary =
                requestMapper.selectMyRequestSummary(0L);

        assertEquals(0L, summary.getTotalCount());
        assertEquals(0L, summary.getDraftCount());
        assertEquals(0L, summary.getPendingApprovalCount());
        assertEquals(0L, summary.getReadyCount());
        assertEquals(0L, summary.getRunningCount());
        assertEquals(0L, summary.getRetryReviewCount());
        assertEquals(0L, summary.getCompletedCount());
        assertEquals(0L, summary.getPartialFailedCount());
        assertEquals(0L, summary.getFailedCount());
        assertEquals(0L, summary.getRejectedCount());
        assertEquals(0L, summary.getWithdrawnCount());

        assertTrue(
                requestMapper.selectMyRequestsPage(
                        0L, "", "", 0L, 5
                ).isEmpty()
        );
    }

    private Long createDraft(String title) {
        RequestDraftCreateRequest input = new RequestDraftCreateRequest();
        input.setTitle("대시보드-" + UUID.randomUUID() + " " + title);
        input.setPurpose("대시보드 집계와 최근 요청 검증");
        input.setSqls(List.of("SELECT 1 FROM DUAL"));

        return requestService.createDraft(input);
    }
}
