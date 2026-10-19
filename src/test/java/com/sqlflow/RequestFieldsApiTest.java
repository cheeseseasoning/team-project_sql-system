package com.sqlflow;

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
class RequestFieldsApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void savesAndReadsAdditionalFields() throws Exception {
        Long requestId = createDraft(fullBody());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailContent")
                        .value("국내 지점만 조회합니다."))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.desiredDate").value("2026-10-23"))
                .andExpect(jsonPath("$.items[0].queryTitle")
                        .value("매출 확인"));
    }

    @Test
    void updatesFieldsAndCanClearOptionalValues() throws Exception {
        Long requestId = createDraft(fullBody());

        String updateBody = """
                {
                    "title": "요청 항목 수정 테스트",
                    "purpose": "추가 항목 수정 확인",
                    "detailContent": "해외 지점도 포함합니다.",
                    "priority": "LOW",
                    "desiredDate": "2026-11-01",
                    "sqls": ["SELECT 1 FROM DUAL"],
                    "sqlTitles": ["매출 확인"]
                }
                """;

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailContent")
                        .value("해외 지점도 포함합니다."))
                .andExpect(jsonPath("$.priority").value("LOW"))
                .andExpect(jsonPath("$.desiredDate").value("2026-11-01"));

        String clearBody = """
                {
                    "title": "요청 항목 수정 테스트",
                    "purpose": "선택 항목 비우기 확인",
                    "detailContent": "   ",
                    "priority": "NORMAL",
                    "desiredDate": null,
                    "sqls": ["SELECT 1 FROM DUAL"],
                    "sqlTitles": ["매출 확인"]
                }
                """;

        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(clearBody)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailContent").doesNotExist())
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.desiredDate").doesNotExist());
    }

    @Test
    void omittedFieldsUseDefaultAndNullValues() throws Exception {
        String body = """
                {
                    "title": "기존 요청 형식 테스트",
                    "purpose": "추가 항목 없이 저장",
                    "sqls": ["SELECT 1 FROM DUAL"]
                }
                """;

        Long requestId = createDraft(body);

        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailContent").doesNotExist())
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.desiredDate").doesNotExist());
    }

    @Test
    void invalidValuesAreRejectedWithoutChangingDraft()
            throws Exception {

        Long requestId = createDraft(fullBody());

        String invalidPriorityBody = fullBody()
                .replace("\"HIGH\"", "\"URGENT\"");

        // 생성 요청의 우선순위 검증
        mockMvc.perform(
                    post("/api/requests/drafts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPriorityBody)
                )
                .andExpect(status().isBadRequest());

        // 수정 요청의 우선순위 검증
        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPriorityBody)
                )
                .andExpect(status().isBadRequest());

        String invalidDateBody = fullBody()
                .replace("2026-10-23", "2026-99-99");

        // 올바르지 않은 날짜 차단
        mockMvc.perform(
                    put("/api/requests/drafts/{requestId}", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDateBody)
                )
                .andExpect(status().isBadRequest());

        // 실패한 수정이 기존 값을 바꾸지 않았는지 확인
        mockMvc.perform(
                    get("/api/requests/mine/{requestId}", requestId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detailContent")
                        .value("국내 지점만 조회합니다."))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.desiredDate").value("2026-10-23"));
    }

    private Long createDraft(String body) throws Exception {
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

    private String fullBody() {
        return """
                {
                    "title": "요청 추가 항목 테스트",
                    "purpose": "추가 항목 저장 확인",
                    "detailContent": "  국내 지점만 조회합니다.  ",
                    "priority": "HIGH",
                    "desiredDate": "2026-10-23",
                    "sqls": ["SELECT 1 FROM DUAL"],
                    "sqlTitles": ["매출 확인"]
                }
                """;
    }
}