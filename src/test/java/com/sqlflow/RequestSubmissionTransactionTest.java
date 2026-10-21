package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
class RequestSubmissionTransactionTest {

    @Autowired
    private RequestService requestService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ApprovalService approvalService;

    private Long requestId;

    @Test
    void approvalFailureRollsBackDatabaseChanges() {

        // 1. 테스트 전용 초안 생성
        RequestDraftCreateRequest draft = new RequestDraftCreateRequest();
        draft.setTitle("제출 롤백 테스트");
        draft.setPurpose("결재 처리 실패 시 DB 롤백 확인");
        draft.setSqls(List.of("SELECT 1 FROM DUAL"));

        requestId = requestService.createDraft(draft, 1L, 1L);

        // 2. 결재 기능을 테스트용 동작으로 대체
        // DB를 변경한 다음 실패하도록 구성
        doAnswer(invocation -> {
            Long targetRequestId = invocation.getArgument(0);

            jdbcTemplate.update(
                    """
                    UPDATE QUERY_REQUEST
                    SET APPROVAL_STATUS = 'PENDING'
                    WHERE REQUEST_ID = ?
                    """,
                    targetRequestId
            );

            throw new IllegalStateException("테스트용 결재 처리 실패");
        }).when(approvalService)
          .createApprovalLine(requestId, 1L, List.of(), 20L);

        RequestSubmitRequest input = new RequestSubmitRequest();
        input.setDbApproverId(20L);

        // 3. 실제 제출 Service 호출
        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> requestService.submitRequest(requestId, input, 1L)
        );

        assertEquals("테스트용 결재 처리 실패", error.getMessage());

        // 4. 제출 실패 후 DB에 남은 값 확인
        String requestStatus = jdbcTemplate.queryForObject(
                """
                SELECT REQUEST_STATUS
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId
        );

        String approvalStatus = jdbcTemplate.queryForObject(
                """
                SELECT APPROVAL_STATUS
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                String.class,
                requestId
        );

        Timestamp submittedAt = jdbcTemplate.queryForObject(
                """
                SELECT SUBMITTED_AT
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """,
                Timestamp.class,
                requestId
        );

        assertEquals("DRAFT", requestStatus);
        assertEquals("NOT_REQUIRED", approvalStatus);
        assertNull(submittedAt);
    }

    @AfterEach
    void cleanUp() {
        if (requestId == null) {
            return;
        }

        // 이번 테스트가 생성한 요청만 정리
        jdbcTemplate.update(
                "DELETE FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                requestId
        );

        jdbcTemplate.update(
                "DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                requestId
        );
    }
}
