package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
class RequestSubmissionPersistenceTest {

    private static final Long USER_ID = 3L;
    private static final Long DEPT_ID = 1L;

    @Autowired
    private RequestService requestService;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private RequestSqlValidationService validator;

    @MockitoBean
    private ApprovalService approvalService;

    private Long requestId;
    private List<Long> queryIds;

    @BeforeEach
    void createDraft() {
        RequestDraftCreateRequest draft = new RequestDraftCreateRequest();
        draft.setTitle("검증 결과 저장 자동 테스트");
        draft.setPurpose("분류 저장 및 제출 실패 롤백 확인");
        draft.setSqls(List.of(
                "SELECT * FROM sqlflow_demo.demo_product",
                "SELECT * FROM sqlflow_demo.demo_customer"
        ));

        requestId = requestService.createDraft(draft, USER_ID, DEPT_ID);

        queryIds = jdbc.queryForList("""
                SELECT QUERY_ID
                FROM QUERY_ITEM
                WHERE REQUEST_ID = ?
                ORDER BY QUERY_ORDER
                """, Long.class, requestId);
    }

    @Test
    void generalItemsSaveGeneralRequest() {
        stubValidation("GENERAL");

        requestService.submitRequest(requestId, submitInput(), USER_ID);

        assertEquals("GENERAL", requestClass());
        assertEquals("PENDING_APPROVAL", requestStatus());

        for (Long queryId : queryIds) {
            assertItem(queryId, "GENERAL");
        }

        assertUsage();
    }

    @Test
    void sensitiveItemMakesWholeRequestSensitive() {
        stubValidation("SENSITIVE");

        requestService.submitRequest(requestId, submitInput(), USER_ID);

        assertEquals("SENSITIVE", requestClass());
        assertEquals("PENDING_APPROVAL", requestStatus());

        assertItem(queryIds.get(0), "GENERAL");
        assertItem(queryIds.get(1), "SENSITIVE");
        assertUsage();
    }

    @Test
    void validationFailureLeavesDatabaseUnchanged() {
        Map<String, Object> before = snapshot();

        when(validator.validateForSubmission(eq(USER_ID), anyList()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "테스트용 검증 실패"
                ));

        assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(
                        requestId, submitInput(), USER_ID)
        );

        assertEquals(before, snapshot());
        verifyNoInteractions(approvalService);
    }

    @Test
    void approvalFailureRollsBackValidationAndUsage() {
        stubValidation("SENSITIVE");
        Map<String, Object> before = snapshot();

        doAnswer(invocation -> {
            // 결재 단계에 도달하기 전에 저장이 수행됐는지 확인
            assertEquals("SENSITIVE", requestClass());
            assertItem(queryIds.get(0), "GENERAL");
            assertItem(queryIds.get(1), "SENSITIVE");
            assertUsage();

            // 결재 처리 중 변경도 함께 롤백되는지 확인
            jdbc.update("""
                    UPDATE QUERY_REQUEST
                    SET APPROVAL_STATUS = 'PENDING'
                    WHERE REQUEST_ID = ?
                    """, requestId);

            throw new IllegalStateException("테스트용 결재 실패");
        }).when(approvalService).createApprovalLine(
                requestId, USER_ID, List.of(), 20L
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> requestService.submitRequest(
                        requestId, submitInput(), USER_ID)
        );

        assertEquals("테스트용 결재 실패", error.getMessage());

        // 요청 분류·검증 상태·시각·대상 목록 등이 모두 원상복구
        assertEquals(before, snapshot());
    }

    private void stubValidation(String secondClass) {
        when(validator.validateForSubmission(eq(USER_ID), anyList()))
                .thenAnswer(invocation -> {
                    List<RequestItemResponse> items =
                            invocation.getArgument(1);

                    return List.of(
                            new RequestSqlValidationResult(
                                    items.get(0).getQueryId(),
                                    "VALID", "테스트 검증 통과",
                                    "GENERAL", List.of(1L)
                            ),
                            new RequestSqlValidationResult(
                                    items.get(1).getQueryId(),
                                    "VALID", "테스트 검증 통과",
                                    secondClass, List.of(4L)
                            )
                    );
                });
    }

    private void assertItem(Long queryId, String expectedClass) {
        Map<String, Object> item = jdbc.queryForMap("""
                SELECT VALIDATION_STATUS, ITEM_CLASS, VALIDATED_AT
                FROM QUERY_ITEM
                WHERE QUERY_ID = ?
                """, queryId);

        assertEquals("VALID", item.get("VALIDATION_STATUS"));
        assertEquals(expectedClass, item.get("ITEM_CLASS"));
        assertNotNull(item.get("VALIDATED_AT"));
    }

    private void assertUsage() {
        assertEquals(
                List.of(1L),
                jdbc.queryForList("""
                        SELECT RESOURCE_ID
                        FROM QUERY_RESOURCE_USAGE
                        WHERE QUERY_ID = ?
                        ORDER BY RESOURCE_ID
                        """, Long.class, queryIds.get(0))
        );

        assertEquals(
                List.of(4L),
                jdbc.queryForList("""
                        SELECT RESOURCE_ID
                        FROM QUERY_RESOURCE_USAGE
                        WHERE QUERY_ID = ?
                        ORDER BY RESOURCE_ID
                        """, Long.class, queryIds.get(1))
        );
    }

    private String requestClass() {
        return jdbc.queryForObject("""
                SELECT REQUEST_CLASS
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, String.class, requestId);
    }

    private String requestStatus() {
        return jdbc.queryForObject("""
                SELECT REQUEST_STATUS
                FROM QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, String.class, requestId);
    }

    private Map<String, Object> snapshot() {
        return Map.of(
                "request", jdbc.queryForMap("""
                        SELECT REQUEST_CLASS, REQUEST_STATUS,
                               APPROVAL_STATUS, SUBMITTED_AT
                        FROM QUERY_REQUEST
                        WHERE REQUEST_ID = ?
                        """, requestId),

                "items", jdbc.queryForList("""
                        SELECT QUERY_ID, VALIDATION_STATUS,
                               ITEM_CLASS, VALIDATED_AT
                        FROM QUERY_ITEM
                        WHERE REQUEST_ID = ?
                        ORDER BY QUERY_ORDER
                        """, requestId),

                "usage", jdbc.queryForList("""
                        SELECT U.QUERY_ID, U.RESOURCE_ID
                        FROM QUERY_RESOURCE_USAGE U
                        JOIN QUERY_ITEM I ON I.QUERY_ID = U.QUERY_ID
                        WHERE I.REQUEST_ID = ?
                        ORDER BY U.QUERY_ID, U.RESOURCE_ID
                        """, requestId)
        );
    }

    private RequestSubmitRequest submitInput() {
        RequestSubmitRequest input = new RequestSubmitRequest();
        input.setDbApproverId(20L);
        return input;
    }

    @AfterEach
    void cleanUp() {
        if (requestId == null) {
            return;
        }

        jdbc.update("""
                DELETE FROM QUERY_RESOURCE_USAGE
                WHERE QUERY_ID IN (
                    SELECT QUERY_ID
                    FROM QUERY_ITEM
                    WHERE REQUEST_ID = ?
                )
                """, requestId);

        jdbc.update(
                "DELETE FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                requestId
        );

        jdbc.update(
                "DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                requestId
        );
    }
}