package com.sqlflow.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestDraftApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createDraftSavesRequestAndSqlItems() throws Exception {
        String body = """
                {
                    "title": "초안 저장 테스트",
                    "purpose": "Oracle 저장 확인",
                    "sqls": [
                        "SELECT 1 FROM DUAL",
                        "SELECT USER FROM DUAL"
                    ],
                    "sqlTitles": [
                        "상수 조회",
                        "사용자 조회"
                    ]
                }
                """;

        Long requestId = postDraft(body);

        String title = jdbcTemplate.queryForObject("""
                SELECT TITLE
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, String.class, requestId);

        String requestStatus = jdbcTemplate.queryForObject("""
                SELECT REQUEST_STATUS
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, String.class, requestId);

        Integer itemCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_ITEM
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);

        assertEquals("초안 저장 테스트", title);
        assertEquals("DRAFT", requestStatus);
        assertEquals(Integer.valueOf(2), itemCount);

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("상수 조회"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("사용자 조회"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }

    @Test
    void validateDraftPersistsPerItemFailureAndReason() throws Exception {
        // SQL 원문은 의도적으로 잘못 입력하되 제목은 정상 입력한다.
        String body = """
                {
                    "title": "SQL 검증 상태 테스트",
                    "purpose": "잘못된 SQL의 검증 상태 확인",
                    "sqls": ["sql"],
                    "sqlTitles": ["잘못된 SQL 검증"]
                }
                """;

        Long requestId = postDraft(body);

        mockMvc.perform(
                    post("/api/requests/{requestId}/validate", requestId)
                        .with(csrf())
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].validationStatus")
                        .value("INVALID"))
                .andExpect(jsonPath("$[0].message").isNotEmpty());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].validationStatus")
                        .value("INVALID"));
    }

    @Test
    void updateDraftChangesRequestAndSqlItems() throws Exception {
        Long requestId = createDraftForUpdateTest();

        String originalRequestNo = jdbcTemplate.queryForObject("""
                SELECT REQUEST_NO
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, String.class, requestId);

        String body = """
                {
                    "title": "수정한 제목",
                    "purpose": "수정한 조회 목적",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ],
                    "sqlTitles": [
                        "기준 일시 조회"
                    ]
                }
                """;

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .with(csrf())
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.requestNo")
                        .value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("수정한 제목"))
                .andExpect(jsonPath("$.purpose")
                        .value("수정한 조회 목적"))
                .andExpect(jsonPath("$.requestStatus").value("DRAFT"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].queryOrder").value(1))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("기준 일시 조회"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));
    }

    @Test
    void updateDraftRejectsSubmittedRequest() throws Exception {
        Long requestId = createDraftForUpdateTest();
        markSubmitted(requestId);

        // 입력값 검증은 통과시켜 제출 상태의 수정 차단을 확인한다.
        String body = """
                {
                    "title": "변경되면 안 되는 제목",
                    "purpose": "변경되면 안 되는 목적",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ],
                    "sqlTitles": [
                        "기준 일시 조회"
                    ]
                }
                """;

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .with(csrf())
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isConflict());

        assertSubmittedRequestUnchanged(requestId);
    }

    @Test
    void deleteDraftRemovesRequestAndSqlItems() throws Exception {
        Long requestId = createDraftForUpdateTest();

        mockMvc.perform(
                    delete("/api/requests/drafts/{requestId}", requestId)
                        .with(csrf())
                        .with(authenticatedUser())
                )
                .andExpect(status().isNoContent());

        Integer requestCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);

        Integer itemCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_ITEM
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);

        assertEquals(Integer.valueOf(0), requestCount);
        assertEquals(Integer.valueOf(0), itemCount);

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDraftRejectsSubmittedRequest() throws Exception {
        Long requestId = createDraftForUpdateTest();
        markSubmitted(requestId);

        mockMvc.perform(
                    delete("/api/requests/drafts/{requestId}", requestId)
                        .with(csrf())
                        .with(authenticatedUser())
                )
                .andExpect(status().isConflict());

        assertSubmittedRequestUnchanged(requestId);
    }

    private Long createDraftForUpdateTest() throws Exception {
        String body = """
                {
                    "title": "수정 전 제목",
                    "purpose": "수정 전 목적",
                    "sqls": [
                        "SELECT 1 FROM DUAL",
                        "SELECT USER FROM DUAL"
                    ],
                    "sqlTitles": [
                        "상수 조회",
                        "사용자 조회"
                    ]
                }
                """;

        return postDraft(body);
    }

    private Long postDraft(String body) throws Exception {
        String response = mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(csrf())
                        .with(authenticatedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.requestId");
        return id.longValue();
    }

    private void markSubmitted(Long requestId) {
        // 수정·삭제의 상태 검사를 위해 테스트용 요청만 변경한다.
        int updated = jdbcTemplate.update("""
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'PENDING_APPROVAL',
                    APPROVAL_STATUS = 'PENDING',
                    SUBMITTED_AT = SYSTIMESTAMP
                WHERE REQUEST_ID = ?
                """, requestId);

        assertEquals(1, updated);
    }

    private void assertSubmittedRequestUnchanged(
            Long requestId) throws Exception {

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 전 제목"))
                .andExpect(jsonPath("$.purpose").value("수정 전 목적"))
                .andExpect(jsonPath("$.requestStatus")
                        .value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("상수 조회"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("사용자 조회"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }

    private RequestPostProcessor authenticatedUser() {
        UserAccount account = new UserAccount();
        account.setUserId(1L);
        account.setDeptId(1L);
        account.setLoginId("test-user");
        account.setPasswordHash("{bcrypt}test");
        account.setUserName("테스트 사용자");
        account.setDeptName("테스트팀");
        account.setPositionName("사원");
        account.setEnabled("Y");

        return user(new SqlFlowUserDetails(account, List.of()));
    }
}