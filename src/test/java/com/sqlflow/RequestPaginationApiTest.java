package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestPaginationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestMapper requestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void pagesContainDifferentRequestsInNewestFirstOrder() throws Exception {
        String prefix = uniquePrefix();
        List<Long> expectedIds = new ArrayList<>();

        // 12건 생성 → 5건씩 조회하면 3페이지
        for (int index = 1; index <= 12; index++) {
            expectedIds.add(createDraft(prefix + " 요청 " + index));
        }

        Collections.reverse(expectedIds);

        for (int page = 1; page <= 3; page++) {
            int start = (page - 1) * 5;
            int end = Math.min(start + 5, expectedIds.size());

            String response = mockMvc.perform(
                        get("/api/requests/mine/page")
                            .param("keyword", prefix)
                            .param("page", String.valueOf(page))
                            .param("size", "5")
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page").value(page))
                    .andExpect(jsonPath("$.size").value(5))
                    .andExpect(jsonPath("$.totalCount").value(12))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.items.length()")
                            .value(end - start))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            assertEquals(
                    expectedIds.subList(start, end),
                    readRequestIds(response)
            );
        }
    }

    @Test
    void countAndItemsUseSameSearchStatusAndDrafterConditions()
            throws Exception {

        String prefix = uniquePrefix();

        for (int index = 1; index <= 5; index++) {
            createDraft(prefix + " 매출 " + index);
        }

        Long pendingId = createDraft(prefix + " 매출 결재");

        jdbcTemplate.update(
                """
                UPDATE QUERY_REQUEST
                SET REQUEST_STATUS = 'PENDING_APPROVAL',
                    APPROVAL_STATUS = 'PENDING',
                    SUBMITTED_AT = SYSTIMESTAMP
                WHERE REQUEST_ID = ?
                """,
                pendingId
        );

        createDraft(prefix + " 인사");

        // 매출 + 초안 조건에 맞는 요청은 5건
        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("keyword", prefix + " 매출")
                        .param("status", "DRAFT")
                        .param("page", "2")
                        .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalCount").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].requestStatus")
                        .value("DRAFT"))
                .andExpect(jsonPath("$.items[1].requestStatus")
                        .value("DRAFT"));

        // 다른 작성자 조건에서는 건수와 목록 모두 없어야 함
        assertEquals(
                0L,
                requestMapper.countMyRequests(0L, prefix, "")
        );

        assertTrue(
                requestMapper.selectMyRequestsPage(
                        0L, prefix, "", 0L, 10
                ).isEmpty()
        );
    }

    @Test
    void excessivePageIsAdjustedAndEmptyResultIsHandled()
            throws Exception {

        String prefix = uniquePrefix();

        for (int index = 1; index <= 3; index++) {
            createDraft(prefix + " 요청 " + index);
        }

        // 3건을 2건씩 조회 → 마지막은 2페이지
        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("keyword", prefix)
                        .param("page", "999")
                        .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));

        // 검색 결과가 없으면 빈 목록 반환
        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("keyword", prefix + " 없는검색어")
                        .param("page", "999")
                        .param("size", "2")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void invalidPageOrSizeReturnsBadRequest() throws Exception {
        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("page", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("size", "0")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                    get("/api/requests/mine/page")
                        .param("size", "51")
                )
                .andExpect(status().isBadRequest());
    }

    private Long createDraft(String title) {
        RequestDraftCreateRequest input = new RequestDraftCreateRequest();
        input.setTitle(title);
        input.setPurpose("페이지 조회 테스트");
        input.setSqls(List.of("SELECT 1 FROM DUAL"));

        return requestService.createDraft(input);
    }

    private List<Long> readRequestIds(String response) {
        List<Number> ids = JsonPath.read(
                response, "$.items[*].requestId");

        return ids.stream()
                .map(Number::longValue)
                .toList();
    }

    private String uniquePrefix() {
        return "페이지검증-" + UUID.randomUUID();
    }
}