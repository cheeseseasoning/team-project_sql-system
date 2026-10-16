package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}