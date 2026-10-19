package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

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
                    ]
                }
                """;

        // 새 API 호출
        String response = mockMvc.perform(
                    post("/api/requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.requestId");
        Long requestId = id.longValue();

        // 실제 Oracle에 요청서가 저장됐는지 확인
        String title = jdbcTemplate.queryForObject(
                "SELECT TITLE FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId
        );

        String requestStatus = jdbcTemplate.queryForObject(
                "SELECT REQUEST_STATUS FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId
        );

        // 같은 요청에 SQL 두 개가 연결됐는지 확인
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                Integer.class,
                requestId
        );

        assertEquals("초안 저장 테스트", title);
        assertEquals("DRAFT", requestStatus);
        assertEquals(Integer.valueOf(2), itemCount);
    }
    
    @Test
    void updateDraftChangesRequestAndSqlItems() throws Exception {

        Long requestId = createDraftForUpdateTest();

        String originalRequestNo = jdbcTemplate.queryForObject(
                "SELECT REQUEST_NO FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId
        );

        String body = """
                {
                    "title": "수정한 제목",
                    "purpose": "수정한 조회 목적",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ]
                }
                """;

        // 기존 초안 수정
        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isNoContent());

        // 상세 조회로 수정 결과 확인
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId))
                .andExpect(jsonPath("$.requestNo").value(originalRequestNo))
                .andExpect(jsonPath("$.title").value("수정한 제목"))
                .andExpect(jsonPath("$.purpose").value("수정한 조회 목적"))
                .andExpect(jsonPath("$.requestStatus").value("DRAFT"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].queryOrder").value(1))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));
    }

    @Test
    void updateDraftRejectsSubmittedRequest() throws Exception {

        Long requestId = createDraftForUpdateTest();

        // 제출 API가 아직 없으므로 테스트용 요청만 결재 대기 상태로 변경
        jdbcTemplate.update(
                """
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'PENDING_APPROVAL',
                    APPROVAL_STATUS = 'PENDING',
                    SUBMITTED_AT = SYSTIMESTAMP
                WHERE REQUEST_ID = ?
                """,
                requestId
        );

        String body = """
                {
                    "title": "변경되면 안 되는 제목",
                    "purpose": "변경되면 안 되는 목적",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ]
                }
                """;

        // 제출된 요청은 수정할 수 없어야 함
        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isConflict());

        // 차단 후 기존 제목과 SQL이 유지되는지 확인
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 전 제목"))
                .andExpect(jsonPath("$.purpose").value("수정 전 목적"))
                .andExpect(jsonPath("$.requestStatus")
                        .value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }

    // 각 테스트에서 사용할 새 초안 생성
    private Long createDraftForUpdateTest() throws Exception {

        String body = """
                {
                    "title": "수정 전 제목",
                    "purpose": "수정 전 목적",
                    "sqls": [
                        "SELECT 1 FROM DUAL",
                        "SELECT USER FROM DUAL"
                    ]
                }
                """;

        String response = mockMvc.perform(
                    post("/api/requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.requestId");
        return id.longValue();
    }
    
    @Test
    void deleteDraftRemovesRequestAndSqlItems() throws Exception {

        Long requestId = createDraftForUpdateTest();

        // 초안 삭제
        mockMvc.perform(
                    delete("/api/requests/drafts/{requestId}", requestId)
                )
                .andExpect(status().isNoContent());

        // 요청서가 실제로 삭제됐는지 확인
        Integer requestCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                Integer.class,
                requestId
        );

        // 연결된 SQL 항목도 삭제됐는지 확인
        Integer itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                Integer.class,
                requestId
        );

        assertEquals(Integer.valueOf(0), requestCount);
        assertEquals(Integer.valueOf(0), itemCount);

        // 삭제한 요청 상세 조회는 404
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDraftRejectsSubmittedRequest() throws Exception {

        Long requestId = createDraftForUpdateTest();

        // 테스트용 요청만 제출 상태로 변경
        jdbcTemplate.update(
                """
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'PENDING_APPROVAL',
                    APPROVAL_STATUS = 'PENDING',
                    SUBMITTED_AT = SYSTIMESTAMP
                WHERE REQUEST_ID = ?
                """,
                requestId
        );

        // 제출된 요청은 삭제할 수 없어야 함
        mockMvc.perform(
                    delete("/api/requests/drafts/{requestId}", requestId)
                )
                .andExpect(status().isConflict());

        // 요청서와 SQL이 그대로 유지되는지 확인
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 전 제목"))
                .andExpect(jsonPath("$.requestStatus")
                        .value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }
}