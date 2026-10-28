package com.sqlflow;

import static com.sqlflow.RequestTestUsers.authenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.RequestService;

@SpringBootTest
@AutoConfigureMockMvc
class RequestWithdrawalIntegrationTest {

    private static final Long DRAFTER_ID = 1L;
    private static final Long DEPT_ID = 1L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RequestService requestService;

    @Autowired
    private ApprovalService approvalService;

    @MockitoSpyBean
    private RequestMapper requestMapper;

    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;

    private Long requestId;
    private Long approverId;

    @Autowired
    void configure(
            @Qualifier("managementDataSource")
            DataSource dataSource,
            @Qualifier("managementTransactionManager")
            PlatformTransactionManager transactionManager) {

        jdbc = new JdbcTemplate(dataSource);
        transaction = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void createFixture() {
    	/*
    	 * 별도 스레드의 승인·회수 트랜잭션에서 조회할 수 있도록
    	 * 테스트 데이터를 미리 커밋한다.
    	 */
        transaction.executeWithoutResult(tx -> {
            approverId = jdbc.queryForObject(
                    "SELECT SEQ_USERS.NEXTVAL FROM DUAL",
                    Long.class);

            String loginId = "withdraw-"
                    + UUID.randomUUID().toString().replace("-", "");

            // 기존 사용자·권한을 수정하지 않고 테스트 결재자를 생성한다.
            int inserted = jdbc.update(
                    """
                    INSERT INTO USERS (
                        USER_ID,
                        DEPT_ID,
                        POSITION_ID,
                        LOGIN_ID,
                        PASSWORD_HASH,
                        USER_NAME,
                        ENABLED,
                        CREATED_AT
                    )
                    SELECT ?,
                           DEPT_ID,
                           POSITION_ID,
                           ?,
                           ?,
                           ?,
                           'Y',
                           SYSTIMESTAMP
                    FROM USERS
                    WHERE USER_ID = ?
                    """,
                    approverId,
                    loginId,
                    "unused-test-password-hash",
                    "회수 테스트 결재자",
                    DRAFTER_ID);

            assertEquals(1, inserted);

            RequestDraftCreateRequest input =
                    new RequestDraftCreateRequest();

            input.setTitle("기안서 회수 테스트");
            input.setPurpose("회수 정책과 트랜잭션 검증");
            input.setSqls(List.of("SELECT 1 FROM DUAL"));
            input.setSqlTitles(List.of("테스트 SQL"));

            requestId = requestService.createDraft(
                    input, DRAFTER_ID, DEPT_ID);

            jdbc.update(
                    """
                    UPDATE QUERY_REQUEST
                    SET REQUEST_STATUS = 'PENDING_APPROVAL',
                        APPROVAL_STATUS = 'PENDING',
                        SUBMITTED_AT = SYSTIMESTAMP
                    WHERE REQUEST_ID = ?
                    """,
                    requestId);

            // 작성자가 아닌 활성 사용자의 중간 결재를 첫 단계로 설정한다.
            insertApproval(
                    approverId, 1, "INTERMEDIATE", "PENDING");

            // 첫 승인 이후 다음 단계가 활성화되는지 검증할 후속 결재.
            insertApproval(
                    DRAFTER_ID, 2, "DB_EXECUTION", "WAITING");
        });
    }

    @AfterEach
    void cleanUp() {
        transaction.executeWithoutResult(tx -> {
            if (requestId != null) {
                jdbc.update(
                        "DELETE FROM APPROVAL WHERE REQUEST_ID = ?",
                        requestId);

                jdbc.update(
                        "DELETE FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                        requestId);

                jdbc.update(
                        "DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                        requestId);
            }

            if (approverId != null) {
                jdbc.update(
                        "DELETE FROM USERS WHERE USER_ID = ?",
                        approverId);
            }
        });
    }

    @Test
    void authorCanWithdrawBeforeFirstApproval() throws Exception {
        List<Long> originalApprovalIds = approvalIds();

        mockMvc.perform(
                    post("/api/requests/{requestId}/withdraw", requestId)
                        .with(authenticatedUser())
                        .with(csrf())
                )
                .andExpect(status().isNoContent());

        assertRequestState("WITHDRAWN", "CANCELLED");
        assertEquals(
                List.of("CANCELLED", "CANCELLED"),
                approvalStatuses());

        // 결재선을 삭제하거나 새로 만들지 않아야 한다.
        assertEquals(originalApprovalIds, approvalIds());

        assertEquals(
                2,
                count("""
                      SELECT COUNT(*)
                      FROM APPROVAL
                      WHERE REQUEST_ID = ?
                        AND DECIDED_AT IS NOT NULL
                        AND COMMENT_TEXT IS NOT NULL
                      """));

        // SQL 내용도 유지한다.
        assertEquals(
                "SELECT 1 FROM DUAL",
                jdbc.queryForObject(
                        """
                        SELECT SQL_TEXT
                        FROM QUERY_ITEM
                        WHERE REQUEST_ID = ?
                        """,
                        String.class,
                        requestId));
    }

    @Test
    void anotherUserCannotWithdraw() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.withdrawRequest(
                        requestId, approverId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        assertRequestState("PENDING_APPROVAL", "PENDING");
        assertEquals(
                List.of("PENDING", "WAITING"),
                approvalStatuses());
    }

    @Test
    void authorCannotWithdrawAfterFirstApproval() {
        // 실제 승인 서비스를 사용해 첫 승인을 처리한다.
        approvalService.approve(requestId, approverId);

        assertEquals(
                List.of("APPROVED", "PENDING"),
                approvalStatuses());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.withdrawRequest(
                        requestId, DRAFTER_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        assertRequestState("PENDING_APPROVAL", "PENDING");
        assertEquals(
                List.of("APPROVED", "PENDING"),
                approvalStatuses());
    }

    @Test
    void withdrawnRequestCannotBeWithdrawnAgain() {
        requestService.withdrawRequest(requestId, DRAFTER_ID);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.withdrawRequest(
                        requestId, DRAFTER_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        assertRequestState("WITHDRAWN", "CANCELLED");
        assertEquals(
                List.of("CANCELLED", "CANCELLED"),
                approvalStatuses());
    }

    @Test
    void unauthenticatedUserCannotWithdraw() throws Exception {
        mockMvc.perform(
                    post("/api/requests/{requestId}/withdraw", requestId)
                        .with(csrf())
                )
                .andExpect(status().isUnauthorized());

        assertRequestState("PENDING_APPROVAL", "PENDING");
        assertEquals(
                List.of("PENDING", "WAITING"),
                approvalStatuses());
    }

    @Test
    void missingCsrfTokenCannotWithdraw() throws Exception {
        mockMvc.perform(
                    post("/api/requests/{requestId}/withdraw", requestId)
                        .with(authenticatedUser())
                )
                .andExpect(status().isForbidden());

        assertRequestState("PENDING_APPROVAL", "PENDING");
        assertEquals(
                List.of("PENDING", "WAITING"),
                approvalStatuses());
    }

    @Test
    void approvalCancellationFailureRollsBackAllChanges() {
        /*
         * 요청 상태가 변경된 뒤 결재선 일부를 변경하고 실패하도록 한다.
         * 예외가 호출자에게 전달된 다음, 독립된 DB 조회로 롤백을 확인한다.
         */
        doAnswer(invocation -> {
            Long targetId = invocation.getArgument(0);

            jdbc.update(
                    """
                    UPDATE APPROVAL
                    SET STATUS = 'CANCELLED',
                        COMMENT_TEXT = '테스트 중 일부 변경',
                        DECIDED_AT = SYSTIMESTAMP
                    WHERE REQUEST_ID = ?
                      AND APPROVAL_ORDER = 1
                    """,
                    targetId);

            throw new IllegalStateException(
                    "테스트용 결재선 처리 실패");
        }).when(requestMapper)
          .cancelApprovalsForWithdrawal(requestId);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> requestService.withdrawRequest(
                        requestId, DRAFTER_ID));

        assertEquals(
                "테스트용 결재선 처리 실패",
                error.getMessage());

        // 요청 상태와 결재선의 일부 변경 모두 롤백돼야 한다.
        assertRequestState("PENDING_APPROVAL", "PENDING");
        assertEquals(
                List.of("PENDING", "WAITING"),
                approvalStatuses());

        assertEquals(
                0,
                count("""
                      SELECT COUNT(*)
                      FROM APPROVAL
                      WHERE REQUEST_ID = ?
                        AND (
                            COMMENT_TEXT IS NOT NULL
                            OR DECIDED_AT IS NOT NULL
                        )
                      """));
    }

    @Test
    void approvalAndWithdrawalCannotBothSucceed() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);

        try {
            Callable<Boolean> approve = () -> {
                start.await(10, TimeUnit.SECONDS);

                return succeedsOrConflicts(
                        () -> approvalService.approve(
                                requestId, approverId));
            };

            Callable<Boolean> withdraw = () -> {
                start.await(10, TimeUnit.SECONDS);

                return succeedsOrConflicts(
                        () -> requestService.withdrawRequest(
                                requestId, DRAFTER_ID));
            };

            var approvalFuture = executor.submit(approve);
            var withdrawalFuture = executor.submit(withdraw);

            boolean approved =
                    approvalFuture.get(30, TimeUnit.SECONDS);

            boolean withdrawn =
                    withdrawalFuture.get(30, TimeUnit.SECONDS);

            // 성공은 정확히 하나여야 한다.
            assertEquals(
                    1,
                    (approved ? 1 : 0) + (withdrawn ? 1 : 0));

            if (withdrawn) {
                assertRequestState("WITHDRAWN", "CANCELLED");
                assertEquals(
                        List.of("CANCELLED", "CANCELLED"),
                        approvalStatuses());
            } else {
                assertRequestState("PENDING_APPROVAL", "PENDING");
                assertEquals(
                        List.of("APPROVED", "PENDING"),
                        approvalStatuses());
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private boolean succeedsOrConflicts(Runnable action) {
        try {
            action.run();
            return true;
        } catch (ResponseStatusException error) {
            assertEquals(
                    HttpStatus.CONFLICT,
                    error.getStatusCode());
            return false;
        }
    }

    private void insertApproval(
            Long userId,
            int order,
            String type,
            String status) {

        int inserted = jdbc.update(
                """
                INSERT INTO APPROVAL (
                    APPROVAL_ID,
                    REQUEST_ID,
                    APPROVER_ID,
                    APPROVAL_ORDER,
                    APPROVAL_TYPE,
                    STATUS,
                    CREATED_AT
                ) VALUES (
                    SEQ_APPROVAL.NEXTVAL,
                    ?, ?, ?, ?, ?,
                    SYSTIMESTAMP
                )
                """,
                requestId,
                userId,
                order,
                type,
                status);

        assertEquals(1, inserted);
    }

    private void assertRequestState(
            String requestStatus,
            String approvalStatus) {

        assertEquals(
                requestStatus,
                jdbc.queryForObject(
                        """
                        SELECT REQUEST_STATUS
                        FROM QUERY_REQUEST
                        WHERE REQUEST_ID = ?
                        """,
                        String.class,
                        requestId));

        assertEquals(
                approvalStatus,
                jdbc.queryForObject(
                        """
                        SELECT APPROVAL_STATUS
                        FROM QUERY_REQUEST
                        WHERE REQUEST_ID = ?
                        """,
                        String.class,
                        requestId));
    }

    private List<String> approvalStatuses() {
        return jdbc.queryForList(
                """
                SELECT STATUS
                FROM APPROVAL
                WHERE REQUEST_ID = ?
                ORDER BY APPROVAL_ORDER
                """,
                String.class,
                requestId);
    }

    private List<Long> approvalIds() {
        return jdbc.queryForList(
                """
                SELECT APPROVAL_ID
                FROM APPROVAL
                WHERE REQUEST_ID = ?
                ORDER BY APPROVAL_ORDER
                """,
                Long.class,
                requestId);
    }

    private int count(String sql) {
        return jdbc.queryForObject(
                sql, Integer.class, requestId);
    }
}