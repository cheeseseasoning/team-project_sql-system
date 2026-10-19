package com.sqlflow;

import static com.sqlflow.RequestTestUsers.authenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestRedraftApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void rejectedRequestCreatesNewDraftAndPreservesOriginal() throws Exception {
        Long sourceId = createSource(true);

        String originalRequestNo = requestNo(sourceId);
        List<Long> originalQueryIds = queryIds(sourceId);

        // 재기안 API 호출
        String response = mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.requestId");
        Long newId = id.longValue();

        // 새 요청 ID·번호와 새 SQL ID가 발급되어야 함
        assertNotEquals(sourceId, newId);
        assertNotEquals(originalRequestNo, requestNo(newId));
        assertEquals(2, queryIds(newId).size());

        for (Long queryId : queryIds(newId)) {
            assertEquals(false, originalQueryIds.contains(queryId));
        }

        // 새 초안이 원본 요청과 연결되어야 함
        Long sourceRequestId = jdbcTemplate.queryForObject(
                """
                SELECT SOURCE_REQUEST_ID
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                Long.class,
                newId
        );

        assertEquals(sourceId, sourceRequestId);

        // 입력 정보는 복사되고 상태는 새 초안으로 초기화
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", newId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRequestId").value(sourceId))
                .andExpect(jsonPath("$.sourceRequestNo").value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("재기안 원본"))
                .andExpect(jsonPath("$.purpose").value("매출 확인"))
                .andExpect(jsonPath("$.detailContent").value("국내 지점만 조회"))
                .andExpect(jsonPath("$.isUrgent").value("Y"))
                .andExpect(jsonPath("$.desiredDate").value("2026-11-01"))
                .andExpect(jsonPath("$.requestStatus").value("DRAFT"))
                .andExpect(jsonPath("$.approvalStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.submittedAt").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryTitle").value("사용자 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT USER FROM DUAL"))
                .andExpect(jsonPath("$.items[0].validationStatus")
                        .value("PENDING"))
                .andExpect(jsonPath("$.items[1].queryTitle").value("날짜 확인"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));

        // 원본 요청·SQL은 그대로 유지
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNo").value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("재기안 원본"))
                .andExpect(jsonPath("$.requestStatus").value("REJECTED"))
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"));

        assertEquals(originalQueryIds, queryIds(sourceId));
    }

    @Test
    void draftRequestCannotBeRedrafted() throws Exception {
        Long sourceId = createSource(false);

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                )
                .andExpect(status().isConflict());

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void anotherUserCannotRedraftRequest() {
        Long sourceId = createSource(true);

        // 원본 작성자가 아닌 사용자 ID로 서비스 호출
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.redraftRequest(sourceId, 0L, 1L)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void missingRequestReturnsNotFound() throws Exception {
        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", -1L)
                        .with(authenticatedUser())
                        .with(csrf())
                )
                .andExpect(status().isNotFound());
    }

    private Long createSource(boolean rejected) {
        RequestDraftCreateRequest input = new RequestDraftCreateRequest();
        input.setTitle("재기안 원본");
        input.setPurpose("매출 확인");
        input.setDetailContent("국내 지점만 조회");
        input.setIsUrgent("Y");
        input.setDesiredDate(LocalDate.of(2026, 11, 1));
        input.setSqls(List.of(
                "SELECT USER FROM DUAL",
                "SELECT SYSDATE FROM DUAL"
        ));
        input.setSqlTitles(List.of("사용자 확인", "날짜 확인"));

        Long requestId = requestService.createDraft(input, 1L, 1L);

        if (rejected) {
            // 이번 테스트에서 만든 요청만 반려 상태로 설정
            jdbcTemplate.update(
                    """
                    UPDATE QUERY_REQUEST
                    SET REQUEST_STATUS = 'REJECTED',
                        APPROVAL_STATUS = 'REJECTED',
                        SUBMITTED_AT = SYSTIMESTAMP
                    WHERE REQUEST_ID = ?
                    """,
                    requestId
            );
        }

        return requestId;
    }

    private String requestNo(Long requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT REQUEST_NO
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId
        );
    }

    private List<Long> queryIds(Long requestId) {
        return jdbcTemplate.queryForList(
                """
                SELECT QUERY_ID
                FROM QUERY_ITEM
                WHERE REQUEST_ID = ?
                ORDER BY QUERY_ORDER, QUERY_ID
                """,
                Long.class,
                requestId
        );
    }

    private int redraftCount(Long sourceId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM QUERY_REQUEST
                WHERE SOURCE_REQUEST_ID = ?
                """,
                Integer.class,
                sourceId
        );
    }
}