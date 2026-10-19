package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.service.ExecutionBatchService;

/**
 * 실제 Oracle에서 실행 대기 등록과 권한·상태 검사를 검증한다.
 * 테스트 데이터는 각 테스트 종료 후 롤백되지만 시퀀스 번호는 복구되지 않는다.
 */
@SpringBootTest
@Transactional
class ExecutionBatchServiceTest {

    @Autowired
    private ExecutionBatchService executionBatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long deptId;

    @BeforeEach
    void findFixtureUser() {
        // 요청·실행 기록의 외래키를 만족시키기 위한 기존 사용자 정보만 읽는다.
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT USER_ID, DEPT_ID
                FROM USERS
                WHERE DEPT_ID IS NOT NULL
                ORDER BY USER_ID
                FETCH FIRST 1 ROW ONLY
                """);

        assertFalse(users.isEmpty(), "테스트에 사용할 부서 소속 사용자가 필요합니다.");

        userId = ((Number) users.get(0).get("USER_ID")).longValue();
        deptId = ((Number) users.get(0).get("DEPT_ID")).longValue();
    }

    @Test
    void approvedReadyRequestCreatesBatchAndExecutionItems() {
        Long requestId = createTestRequest("READY", "APPROVED", 3);

        Long batchId = executionBatchService.createInitialBatch(requestId, createExecutor());

        Map<String, Object> batch = jdbcTemplate.queryForMap("""
                SELECT REQUEST_ID, INITIATED_BY,
                       TRIGGER_TYPE, BATCH_TYPE, BATCH_STATUS
                FROM EXECUTION_BATCH
                WHERE BATCH_ID = ?
                """, batchId);

        assertEquals(requestId, ((Number) batch.get("REQUEST_ID")).longValue());
        assertEquals(userId, ((Number) batch.get("INITIATED_BY")).longValue());
        assertEquals("MANUAL", batch.get("TRIGGER_TYPE"));
        assertEquals("INITIAL", batch.get("BATCH_TYPE"));
        assertEquals("PENDING", batch.get("BATCH_STATUS"));

        List<Long> expectedQueryIds = jdbcTemplate.queryForList("""
                SELECT QUERY_ID
                FROM QUERY_ITEM
                WHERE REQUEST_ID = ?
                ORDER BY QUERY_ID
                """, Long.class, requestId);

        List<Long> actualQueryIds = jdbcTemplate.queryForList("""
                SELECT QUERY_ID
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                ORDER BY QUERY_ID
                """, Long.class, batchId);

        // 개수뿐 아니라 해당 요청의 SQL들과 정확히 연결됐는지도 확인한다.
        assertEquals(3, actualQueryIds.size());
        assertEquals(expectedQueryIds, actualQueryIds);

        Integer pendingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                  AND STATUS = 'PENDING'
                  AND ATTEMPT_NO = 1
                  AND RETRY_OF_EXECUTION_ID IS NULL
                """, Integer.class, batchId);

        assertEquals(Integer.valueOf(3), pendingCount);
    }

    @Test
    void unapprovedRequestIsRejected() {
        Long requestId = createTestRequest("PENDING_APPROVAL", "PENDING", 1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(requestId, createExecutor()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(0, countBatches(requestId));
    }

    @Test
    void duplicateInitialBatchIsRejected() {
        Long requestId = createTestRequest("READY", "APPROVED", 2);

        executionBatchService.createInitialBatch(requestId, createExecutor());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(requestId, createExecutor()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(1, countBatches(requestId));

        Integer executionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_EXECUTION E
                JOIN EXECUTION_BATCH B ON B.BATCH_ID = E.BATCH_ID
                WHERE B.REQUEST_ID = ?
                """, Integer.class, requestId);

        assertEquals(Integer.valueOf(2), executionCount);
    }

    @Test
    void userWithoutExecutionAuthorityIsRejected() {
        Long requestId = createTestRequest("READY", "APPROVED", 1);

        // 활성 사용자이지만 실행 권한은 없는 상황을 구성한다.
        SqlFlowUserDetails user = createUser(List.of());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(requestId, user));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals(0, countBatches(requestId));
    }

    @Test
    void requestNotReadyIsRejected() {
        // 승인 상태만으로 실행되지 않고 요청 상태도 검사하는지 확인한다.
        Long requestId = createTestRequest("WITHDRAWN", "APPROVED", 1);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(requestId, createExecutor()));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(0, countBatches(requestId));
    }

    /** 실행 권한을 가진 테스트용 로그인 사용자 정보를 만든다. */
    private SqlFlowUserDetails createExecutor() {
        return createUser(List.of("DB_APPROVER"));
    }

    /**
     * 실제 로그인 대신 Service에 전달할 사용자 객체를 구성한다.
     * DB의 사용자 역할은 변경하지 않으며, 전달한 역할은 ROLE_ 접두사가 붙는다.
     */
    private SqlFlowUserDetails createUser(List<String> roleCodes) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setDeptId(deptId);
        account.setLoginId("execution-test-user");
        account.setUserName("실행 테스트 사용자");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(account, roleCodes);
    }

    /**
     * 기존 업무 데이터를 변경하지 않도록 테스트 전용 요청과 SQL을 생성한다.
     * 두 상태를 각각 지정해 실행 허용·차단 조건을 구성한다.
     */
    private Long createTestRequest(String requestStatus, String approvalStatus, int sqlCount) {
        Long requestId = jdbcTemplate.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE, REQUEST_STATUS, APPROVAL_STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requestId,
                "TEST-" + requestId,
                userId,
                deptId,
                "실행 등록 테스트",
                "테스트 종료 후 롤백할 데이터",
                requestStatus,
                approvalStatus);

        for (int order = 1; order <= sqlCount; order++) {
            jdbcTemplate.update("""
                    INSERT INTO QUERY_ITEM (
                        QUERY_ID, REQUEST_ID, QUERY_ORDER,
                        QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                    ) VALUES (
                        SEQ_QUERY_ITEM.NEXTVAL, ?, ?, ?, ?, ?
                    )
                    """,
                    requestId,
                    order,
                    "테스트 SQL " + order,
                    "SELECT 1 FROM DUAL",
                    "MANUAL");
        }

        return requestId;
    }

    /** 해당 요청에 생성된 실행 묶음 개수를 확인한다. */
    private int countBatches(Long requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM EXECUTION_BATCH
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);
    }
}