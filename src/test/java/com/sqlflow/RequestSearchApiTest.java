package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestSearchApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestService requestService;

    @Autowired
    private RequestMapper requestMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void keywordAndStatusAreAppliedTogether() throws Exception {
        String prefix = uniquePrefix();

        Long expectedId = createDraft(prefix + " 매출 분석");
        Long pendingId = createDraft(prefix + " 매출 결재");
        createDraft(prefix + " 인사 분석");

        // 상태 필터 확인을 위한 테스트용 데이터
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

        // 제목만 일치하거나 상태만 일치하는 요청은 제외돼야 함
        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("keyword", prefix + " 매출")
                        .param("status", "DRAFT")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(expectedId))
                .andExpect(jsonPath("$[0].requestStatus").value("DRAFT"));

        // 상태 조건을 빼면 매출 요청 두 개 조회
        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("keyword", prefix + " 매출")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchesTitleIgnoringCaseAndSearchesRequestNumber()
            throws Exception {

        String prefix = uniquePrefix();
        Long requestId = createDraft(prefix + " SALES Report");

        // 제목 일부 검색: 영문 대소문자 무시, 앞뒤 공백 제거
        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("keyword", "  " + prefix + " sales  ")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(requestId));

        String requestNo = jdbcTemplate.queryForObject(
                """
                SELECT REQUEST_NO
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId
        );

        // 요청번호 검색
        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("keyword", requestNo)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(requestId));
    }

    @Test
    void anotherDrafterCannotFindRequestByTitleOrNumber() {
        String title = uniquePrefix() + " 작성자 제한";
        Long requestId = createDraft(title);

        String requestNo = jdbcTemplate.queryForObject(
                """
                SELECT REQUEST_NO
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId
        );

        // 요청 작성자는 1번. 다른 작성자 조건으로는 나오면 안 됨.
        // 조회 조건 검사이므로 별도 사용자 생성은 필요 없음.
        assertTrue(
                requestMapper.selectMyRequests(0L, title, "").isEmpty()
        );

        assertTrue(
                requestMapper.selectMyRequests(0L, requestNo, "").isEmpty()
        );
    }

    @Test
    void invalidSearchConditionsReturnBadRequest() throws Exception {
        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("status", "INVALID_STATUS")
                )
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                    get("/api/requests/mine")
                        .param("keyword", "a".repeat(121))
                )
                .andExpect(status().isBadRequest());
    }

    private Long createDraft(String title) {
        RequestDraftCreateRequest input = new RequestDraftCreateRequest();
        input.setTitle(title);
        input.setPurpose("요청 검색 테스트");
        input.setSqls(List.of("SELECT 1 FROM DUAL"));

        return requestService.createDraft(input);
    }

    private String uniquePrefix() {
        return "검색검증-" + UUID.randomUUID();
    }
}