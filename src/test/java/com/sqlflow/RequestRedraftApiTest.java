package com.sqlflow;

import static com.sqlflow.RequestTestUsers.authenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional(transactionManager = "managementTransactionManager")
class RequestRedraftApiTest {

    private static final String REDRAFT_BODY = """
            {
              "title": "수정한 재기안",
              "purpose": "수정한 조회 목적",
              "detailContent": "수정한 상세 내용",
              "isUrgent": "N",
              "desiredDate": "2026-11-02",
              "sqls": [
                "SELECT 1 FROM DUAL",
                "SELECT 2 FROM DUAL"
              ],
              "sqlTitles": [
                "첫 번째 SQL",
                "두 번째 SQL"
              ]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void openingRedraftPageDoesNotCreateDraft() throws Exception {
        Long sourceId = createSource("WITHDRAWN");

        mockMvc.perform(
                    get("/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(view().name("request-edit"));

        // 화면 진입은 원본 조회만 수행해야 한다.
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.title").value("재기안 원본"));

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void returningToOriginalDetailDoesNotCreateDraft() throws Exception {
        Long sourceId = createSource("WITHDRAWN");

        mockMvc.perform(
                    get("/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk());

        // 돌아가기 링크가 가리키는 원본 상세를 조회한다.
        mockMvc.perform(
                    get("/requests/{requestId}", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(view().name("request-detail"));

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void savingRejectedRequestCreatesEditedDraftAndPreservesOriginal()
            throws Exception {

        Long sourceId = createSource("REJECTED");
        String originalRequestNo = requestNo(sourceId);
        List<Long> originalQueryIds = queryIds(sourceId);

        Long newId = saveRedraft(sourceId);

        assertNotEquals(sourceId, newId);
        assertNotEquals(originalRequestNo, requestNo(newId));
        assertEquals(1, redraftCount(sourceId));

        List<Long> newQueryIds = queryIds(newId);
        assertEquals(2, newQueryIds.size());

        for (Long queryId : newQueryIds) {
            assertFalse(originalQueryIds.contains(queryId));
        }

        // 원본 복사본이 아니라 저장 화면에서 수정한 내용이어야 한다.
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", newId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRequestId").value(sourceId))
                .andExpect(jsonPath("$.sourceRequestNo")
                        .value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("수정한 재기안"))
                .andExpect(jsonPath("$.purpose").value("수정한 조회 목적"))
                .andExpect(jsonPath("$.detailContent").value("수정한 상세 내용"))
                .andExpect(jsonPath("$.isUrgent").value("N"))
                .andExpect(jsonPath("$.desiredDate").value("2026-11-02"))
                .andExpect(jsonPath("$.requestStatus").value("DRAFT"))
                .andExpect(jsonPath("$.approvalStatus").value("NOT_REQUIRED"))
                .andExpect(jsonPath("$.submittedAt").doesNotExist())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("첫 번째 SQL"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[0].validationStatus")
                        .value("PENDING"))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("두 번째 SQL"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT 2 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].validationStatus")
                        .value("PENDING"));

        // 새 초안에는 아직 결재선이 없어야 한다.
        assertEquals(0, approvalCount(newId));

        // 원본 내용과 상태는 그대로 보존한다.
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestNo").value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("재기안 원본"))
                .andExpect(jsonPath("$.purpose").value("매출 확인"))
                .andExpect(jsonPath("$.detailContent").value("국내 지점만 조회"))
                .andExpect(jsonPath("$.isUrgent").value("Y"))
                .andExpect(jsonPath("$.desiredDate").value("2026-11-01"))
                .andExpect(jsonPath("$.requestStatus").value("REJECTED"))
                .andExpect(jsonPath("$.approvalStatus").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT USER FROM DUAL"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));

        assertEquals(originalQueryIds, queryIds(sourceId));
    }

    @Test
    void withdrawnRequestCanCreateNewDraft() throws Exception {
        Long sourceId = createSource("WITHDRAWN");

        Long newId = saveRedraft(sourceId);

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", newId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceRequestId").value(sourceId))
                .andExpect(jsonPath("$.title").value("수정한 재기안"))
                .andExpect(jsonPath("$.requestStatus").value("DRAFT"))
                .andExpect(jsonPath("$.approvalStatus").value("NOT_REQUIRED"));

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", sourceId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestStatus").value("WITHDRAWN"))
                .andExpect(jsonPath("$.approvalStatus").value("CANCELLED"));

        assertEquals(1, redraftCount(sourceId));
        assertEquals(0, approvalCount(newId));
    }

    @Test
    void draftRequestCannotBeRedrafted() throws Exception {
        Long sourceId = createSource("DRAFT");

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REDRAFT_BODY)
                )
                .andExpect(status().isConflict());

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void anotherUserCannotRedraftRequest() {
        Long sourceId = createSource("REJECTED");

        // 원본 작성자가 아닌 사용자 ID로 서비스 호출
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.redraftRequest(
                        sourceId,
                        sourceInput(),
                        0L,
                        1L)
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REDRAFT_BODY)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void requestBodyIsRequired() throws Exception {
        Long sourceId = createSource("REJECTED");

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void blankTitleDoesNotCreateDraft() throws Exception {
        Long sourceId = createSource("REJECTED");

        String invalidBody = """
                {
                  "title": "   ",
                  "purpose": "조회 목적",
                  "isUrgent": "N",
                  "sqls": ["SELECT 1 FROM DUAL"],
                  "sqlTitles": ["SQL"]
                }
                """;

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody)
                )
                .andExpect(status().isBadRequest());

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void unauthenticatedUserCannotSaveRedraft() throws Exception {
        Long sourceId = createSource("REJECTED");

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REDRAFT_BODY)
                )
                .andExpect(status().isUnauthorized());

        assertEquals(0, redraftCount(sourceId));
    }

    @Test
    void missingCsrfTokenDoesNotCreateDraft() throws Exception {
        Long sourceId = createSource("REJECTED");

        mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REDRAFT_BODY)
                )
                .andExpect(status().isForbidden());

        assertEquals(0, redraftCount(sourceId));
    }

    private Long saveRedraft(Long sourceId) throws Exception {
        var result = mockMvc.perform(
                    post("/api/requests/{requestId}/redraft", sourceId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REDRAFT_BODY)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andReturn();

        Number id = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.requestId");

        Long newId = id.longValue();

        assertEquals(
                "/api/requests/mine/" + newId,
                result.getResponse().getHeader("Location"));

        return newId;
    }

    private Long createSource(String requestStatus) {
        Long requestId = requestService.createDraft(
                sourceInput(), 1L, 1L);

        if (!"DRAFT".equals(requestStatus)) {
            String approvalStatus = switch (requestStatus) {
                case "REJECTED" -> "REJECTED";
                case "WITHDRAWN" -> "CANCELLED";
                default -> throw new IllegalArgumentException(
                        "지원하지 않는 테스트 상태: " + requestStatus);
            };

            jdbcTemplate.update(
                    """
                    UPDATE QUERY_REQUEST
                    SET REQUEST_STATUS = ?,
                        APPROVAL_STATUS = ?,
                        SUBMITTED_AT = SYSTIMESTAMP
                    WHERE REQUEST_ID = ?
                    """,
                    requestStatus,
                    approvalStatus,
                    requestId);
        }

        return requestId;
    }

    private RequestDraftCreateRequest sourceInput() {
        RequestDraftCreateRequest input = new RequestDraftCreateRequest();

        input.setTitle("재기안 원본");
        input.setPurpose("매출 확인");
        input.setDetailContent("국내 지점만 조회");
        input.setIsUrgent("Y");
        input.setDesiredDate(LocalDate.of(2026, 11, 1));
        input.setSqls(List.of(
                "SELECT USER FROM DUAL",
                "SELECT SYSDATE FROM DUAL"));
        input.setSqlTitles(List.of(
                "사용자 확인",
                "날짜 확인"));

        return input;
    }

    private String requestNo(Long requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT REQUEST_NO
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId);
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
                requestId);
    }

    private int redraftCount(Long sourceId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM QUERY_REQUEST
                WHERE SOURCE_REQUEST_ID = ?
                """,
                Integer.class,
                sourceId);
    }

    private int approvalCount(Long requestId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM APPROVAL
                WHERE REQUEST_ID = ?
                """,
                Integer.class,
                requestId);
    }
}