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

import com.sqlflow.execution.service.ExecutionBatchService;

/**
 * 실제 Oracle에서 실행 묶음과 SQL별 대기 이력 등록을 검증한다.
 * 테스트에서 생성한 데이터는 각 테스트 종료 후 자동 롤백된다.
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
        // 외래키를 만족시키기 위한 기존 사용자 정보만 읽는다.
        // 기존 사용자와 부서 데이터는 변경하지 않는다.
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT USER_ID, DEPT_ID
                FROM USERS
                WHERE DEPT_ID IS NOT NULL
                ORDER BY USER_ID
                FETCH FIRST 1 ROW ONLY
                """);

        assertFalse(users.isEmpty(),
                "테스트에 사용할 부서 소속 사용자가 필요합니다.");

        userId = ((Number) users.get(0).get("USER_ID")).longValue();
        deptId = ((Number) users.get(0).get("DEPT_ID")).longValue();
    }

    @Test
    void approvedRequestCreatesBatchAndExecutionItems() {
        Long requestId = createTestRequest("APPROVED", 3);

        Long batchId =
                executionBatchService.createInitialBatch(requestId, userId);

        Map<String, Object> batch = jdbcTemplate.queryForMap("""
                SELECT REQUEST_ID, INITIATED_BY,
                       TRIGGER_TYPE, BATCH_TYPE, BATCH_STATUS
                FROM EXECUTION_BATCH
                WHERE BATCH_ID = ?
                """, batchId);

        assertEquals(requestId,
                ((Number) batch.get("REQUEST_ID")).longValue());
        assertEquals(userId,
                ((Number) batch.get("INITIATED_BY")).longValue());
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

        // SQL 개수뿐 아니라 올바른 SQL 항목과 연결됐는지도 확인한다.
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
        Long requestId = createTestRequest("PENDING", 1);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(
                        requestId, userId));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals(0, countBatches(requestId));
    }

    @Test
    void duplicateInitialBatchIsRejected() {
        Long requestId = createTestRequest("APPROVED", 2);

        executionBatchService.createInitialBatch(requestId, userId);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> executionBatchService.createInitialBatch(
                        requestId, userId));

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

    /**
     * 테스트 전용 요청과 SQL 항목을 생성한다.
     * 기존 업무 요청의 승인 상태를 바꾸지 않도록 새 데이터를 사용한다.
     */
    private Long createTestRequest(String approvalStatus, int sqlCount) {
        Long requestId = jdbcTemplate.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL",
                Long.class);

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
                "PENDING_APPROVAL",
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

    private int countBatches(Long requestId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM EXECUTION_BATCH
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);
    }
}