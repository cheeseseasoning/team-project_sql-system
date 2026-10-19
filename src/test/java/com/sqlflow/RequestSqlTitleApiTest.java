package com.sqlflow;

import static com.sqlflow.RequestTestUsers.authenticatedUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestSqlTitleApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void savesCustomTitleAndUsesDefaultForBlankTitle() throws Exception {
        Long requestId = createDraft();

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("매출 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("SQL 2"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }

    @Test
    void updatesSqlTitleTogetherWithSqlContent() throws Exception {
        Long requestId = createDraft();

        String body = """
                {
                    "title": "SQL 제목 수정 테스트",
                    "purpose": "SQL 제목과 내용의 연결 확인",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ],
                    "sqlTitles": [
                        "기준 일시 확인"
                    ]
                }
                """;

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].queryOrder").value(1))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("기준 일시 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));
    }

    @Test
    void mismatchedTitleCountIsRejectedWithoutChangingDraft()
            throws Exception {

        Long requestId = createDraft();

        String body = """
                {
                    "title": "잘못된 제목 개수",
                    "purpose": "SQL과 제목 개수 검증",
                    "sqls": [
                        "SELECT SYSDATE FROM DUAL"
                    ],
                    "sqlTitles": [
                        "제목 하나",
                        "제목 둘"
                    ]
                }
                """;

        // 생성에서도 개수 불일치 차단
        mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());

        // 수정에서도 개수 불일치 차단
        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());

        // 기존 요청과 SQL이 그대로 남아 있어야 함
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("SQL 제목 테스트"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("매출 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("SQL 2"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }

    @Test
    void titleLongerThanTwoHundredCharactersIsRejected()
            throws Exception {

        String body = """
                {
                    "title": "제목 길이 검증",
                    "purpose": "SQL 제목 최대 길이 확인",
                    "sqls": [
                        "SELECT 1 FROM DUAL"
                    ],
                    "sqlTitles": [
                        "%s"
                    ]
                }
                """.formatted("a".repeat(201));

        mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());
    }

    private Long createDraft() throws Exception {
        String body = """
                {
                    "title": "SQL 제목 테스트",
                    "purpose": "SQL별 제목 저장 확인",
                    "sqls": [
                        "SELECT 1 FROM DUAL",
                        "SELECT USER FROM DUAL"
                    ],
                    "sqlTitles": [
                        "매출 확인",
                        "   "
                    ]
                }
                """;

        String response = mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(authenticatedUser())
                        .with(csrf())
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
}