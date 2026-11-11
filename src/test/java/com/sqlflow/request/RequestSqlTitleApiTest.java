package com.sqlflow.request;

import static com.sqlflow.request.RequestTestUsers.authenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestSqlTitleApiTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void configureAuthenticatedMockMvc() {
        mockMvc = AuthenticatedMockMvc.forRequester(
                webApplicationContext);
    }

    @Test
    void savesRequiredTitlesInSqlOrder() throws Exception {
        Long requestId = createDraft();

        assertOriginalDraftUnchanged(requestId);
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
                .andExpect(jsonPath("$.title")
                        .value("SQL 제목 수정 테스트"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].queryOrder").value(1))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("기준 일시 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT SYSDATE FROM DUAL"));
    }

    @ParameterizedTest(name = "잘못된 제목 생성 요청 [{index}]")
    @MethodSource("invalidTitleFields")
    void invalidTitlesAreRejectedWithoutCreatingDraft(
            String titleField) throws Exception {

        String requestTitle =
                "SQL 제목 검증-" + UUID.randomUUID();

        String body = invalidRequestBody(
                requestTitle, titleField);

        mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_REQUEST
                WHERE TITLE = ?
                """, Integer.class, requestTitle);

        assertEquals(0, count);
    }

    @ParameterizedTest(name = "잘못된 제목 수정 요청 [{index}]")
    @MethodSource("invalidTitleFields")
    void invalidTitlesAreRejectedWithoutChangingDraft(
            String titleField) throws Exception {

        Long requestId = createDraft();

        String body = invalidRequestBody(
                "변경되면 안 되는 제목", titleField);

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .with(authenticatedUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                )
                .andExpect(status().isBadRequest());

        assertOriginalDraftUnchanged(requestId);
    }

    @Test
    void twoHundredCharacterTitleIsAccepted() throws Exception {
        String sqlTitle = "가".repeat(200);

        String body = """
                {
                    "title": "SQL 제목 길이 경계 테스트",
                    "purpose": "200자 제목 저장 확인",
                    "sqls": [
                        "SELECT 1 FROM DUAL"
                    ],
                    "sqlTitles": [
                        "%s"
                    ]
                }
                """.formatted(sqlTitle);

        Long requestId = postDraft(body);

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value(sqlTitle));
    }

    /**
     * SQL은 한 개를 전달한다.
     * 제목 목록 누락·null·빈 값·개수 불일치·길이 초과를 검사한다.
     */
    static Stream<String> invalidTitleFields() {
        return Stream.of(
                "",
                ", \"sqlTitles\": null",
                ", \"sqlTitles\": []",
                ", \"sqlTitles\": [\"\"]",
                ", \"sqlTitles\": [\"   \"]",
                ", \"sqlTitles\": [null]",
                ", \"sqlTitles\": [\"제목 하나\", \"제목 둘\"]",
                ", \"sqlTitles\": [\"" + "가".repeat(201) + "\"]"
        );
    }

    private String invalidRequestBody(
            String requestTitle,
            String titleField) {

        return """
                {
                    "title": "%s",
                    "purpose": "SQL 제목 필수 입력 검증",
                    "sqls": [
                        "SELECT 1 FROM DUAL"
                    ]
                    %s
                }
                """.formatted(requestTitle, titleField);
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
                        "  매출 확인  ",
                        "사용자 확인"
                    ]
                }
                """;

        return postDraft(body);
    }

    private Long postDraft(String body) throws Exception {
        String response = mockMvc.perform(
                    post("/api/requests/drafts")
                        .with(authenticatedUser())
                        .with(csrf())
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

    private void assertOriginalDraftUnchanged(
            Long requestId) throws Exception {

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title")
                        .value("SQL 제목 테스트"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].queryOrder").value(1))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("매출 확인"))
                .andExpect(jsonPath("$.items[0].sqlText")
                        .value("SELECT 1 FROM DUAL"))
                .andExpect(jsonPath("$.items[1].queryOrder").value(2))
                .andExpect(jsonPath("$.items[1].queryTitle")
                        .value("사용자 확인"))
                .andExpect(jsonPath("$.items[1].sqlText")
                        .value("SELECT USER FROM DUAL"));
    }
}