package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.service.ExecutionBatchRecordService;

/**
 * 실제 Oracle에서 실행 순서·상태 변경·롤백을 확인한다.
 *
 * 서비스 자체의 트랜잭션을 확인하기 위해 테스트에 @Transactional을 붙이지 않는다.
 * 상태 변경 실패를 재현하는 부분만 Spy로 대체하고 나머지는 실제 SQL을 수행한다.
 */
@SpringBootTest(properties = {
        "app.execution.result-directory=${java.io.tmpdir}/sqlflow-test-results",
        "app.execution.query-timeout-seconds=60",
        "app.execution.max-file-bytes=104857600"
})
class ExecutionBatchRecordIntegrationTest {

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;

    @Autowired
    private ExecutionBatchRecordService service;

    @MockitoSpyBean
    private ExecutionBatchMapper mapper;

    private JdbcTemplate jdbc;
    private Long actorId;
    private Long requestId;
    private Long batchId;

    // 요청에 등록된 SQL 순서대로 보관한다.
    private final List<Long> executionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(managementDataSource);

        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT U.USER_ID, U.DEPT_ID
                FROM USERS U
                WHERE U.ENABLED = 'Y'
                  AND U.DEPT_ID IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM USER_ROLE UR
                      JOIN ROLE R ON R.ROLE_ID = UR.ROLE_ID
                      WHERE UR.USER_ID = U.USER_ID
                        AND R.ROLE_CODE = 'DB_APPROVER'
                  )
                ORDER BY U.USER_ID
                FETCH FIRST 1 ROW ONLY
                """);

        assertFalse(
                users.isEmpty(),
                "활성 상태이며 DB_APPROVER 역할이 있는 사용자가 필요합니다.");

        actorId = ((Number) users.get(0).get("USER_ID")).longValue();
        long deptId = ((Number) users.get(0).get("DEPT_ID")).longValue();

        requestId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE, REQUEST_STATUS, APPROVAL_STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, 'READY', 'APPROVED')
                """,
                requestId, "TEST-BATCH-" + requestId,
                actorId, deptId, "묶음 상태 테스트", "상태 변경과 롤백 확인");

        batchId = jdbc.queryForObject(
                "SELECT SEQ_EXECUTION_BATCH.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO EXECUTION_BATCH (
                    BATCH_ID, REQUEST_ID, INITIATED_BY,
                    TRIGGER_TYPE, BATCH_TYPE, BATCH_STATUS, REQUESTED_AT
                ) VALUES (?, ?, ?, 'MANUAL', 'INITIAL', 'PENDING', SYSTIMESTAMP)
                """,
                batchId, requestId, actorId);

        // 일부러 역순으로 생성하여 QUERY_ORDER 정렬 여부를 확인한다.
        for (int order = 2; order >= 1; order--) {
            createExecution(order);
        }
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null) {
            return;
        }

        if (batchId != null) {
            jdbc.update("""
                    DELETE FROM QUERY_EXECUTION
                    WHERE BATCH_ID = ?
                    """, batchId);

            jdbc.update(
                    "DELETE FROM EXECUTION_BATCH WHERE BATCH_ID = ?",
                    batchId);
        }

        if (requestId != null) {
            jdbc.update(
                    "DELETE FROM QUERY_ITEM WHERE REQUEST_ID = ?",
                    requestId);

            jdbc.update(
                    "DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                    requestId);
        }
    }

    @Test
    void startsInQueryOrderAndRejectsSecondStart() {
        var result = service.start(batchId, actorId);

        assertEquals(executionIds, result.executionIds());
        assertEquals("RUNNING", batchStatus());
        assertEquals("RUNNING", requestStatus());

        assertThrows(
                ResponseStatusException.class,
                () -> service.start(batchId, actorId));

        assertEquals("RUNNING", batchStatus());
        assertEquals("RUNNING", requestStatus());
    }

    @Test
    void requestStartFailureRollsBackBatchStart() {
        // 묶음 시작 UPDATE는 실제 실행하고, 뒤의 요청 UPDATE만 실패시킨다.
        doReturn(0).when(mapper).updateRequestStatus(
                requestId, "READY", "RUNNING");

        assertThrows(
                ResponseStatusException.class,
                () -> service.start(batchId, actorId));

        assertEquals("PENDING", batchStatus());
        assertEquals("READY", requestStatus());
    }

    @ParameterizedTest
    @CsvSource({
            "SUCCESS, SUCCESS, SUCCESS, COMPLETED, 2, 0",
            "SUCCESS, FAILED, PARTIAL_FAILED, PARTIAL_FAILED, 1, 1",
            "FAILED, FAILED, FAILED, FAILED, 0, 2"
    })
    void savesAggregatedStatuses(
            String firstStatus,
            String secondStatus,
            String expectedBatchStatus,
            String expectedRequestStatus,
            int successCount,
            int failureCount) {

        service.start(batchId, actorId);
        setExecutionStatuses(firstStatus, secondStatus);

        var result = service.finish(batchId);

        assertEquals(expectedBatchStatus, batchStatus());
        assertEquals(expectedRequestStatus, requestStatus());
        assertEquals(successCount, result.successCount());
        assertEquals(failureCount, result.failureCount());

        assertNotNull(jdbc.queryForObject("""
                SELECT FINISHED_AT
                FROM EXECUTION_BATCH
                WHERE BATCH_ID = ?
                """, java.sql.Timestamp.class, batchId));
    }

    @Test
    void requestCompletionFailureRollsBackBatchCompletion() {
        service.start(batchId, actorId);
        setExecutionStatuses("SUCCESS", "SUCCESS");

        // 묶음 SUCCESS 변경 후 요청 완료 변경이 실패하는 상황.
        doReturn(0).when(mapper).updateRequestStatus(
                requestId, "RUNNING", "COMPLETED");

        assertThrows(
                ResponseStatusException.class,
                () -> service.finish(batchId));

        // 상태와 종료 시각 모두 롤백되어야 한다.
        assertEquals("RUNNING", batchStatus());
        assertEquals("RUNNING", requestStatus());

        assertNull(jdbc.queryForObject("""
                SELECT FINISHED_AT
                FROM EXECUTION_BATCH
                WHERE BATCH_ID = ?
                """, java.sql.Timestamp.class, batchId));
    }

    private void createExecution(int order) {
        Long queryId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_ITEM.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID, REQUEST_ID, QUERY_ORDER,
                    QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                ) VALUES (?, ?, ?, ?, ?, 'MANUAL')
                """,
                queryId, requestId, order, "테스트 SQL " + order,
                "SELECT department_id FROM demo_department");

        Long executionId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_EXECUTION.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_EXECUTION (
                    EXECUTION_ID, BATCH_ID, QUERY_ID, ATTEMPT_NO, STATUS
                ) VALUES (?, ?, ?, 1, 'PENDING')
                """,
                executionId, batchId, queryId);

        executionIds.add(0, executionId);
    }

    /** 이번 테스트는 집계 기능이 대상이므로 SQL별 최종 상태를 직접 구성한다. */
    private void setExecutionStatuses(String firstStatus, String secondStatus) {
        jdbc.update("""
                UPDATE QUERY_EXECUTION
                SET STATUS = ?
                WHERE EXECUTION_ID = ?
                """, firstStatus, executionIds.get(0));

        jdbc.update("""
                UPDATE QUERY_EXECUTION
                SET STATUS = ?
                WHERE EXECUTION_ID = ?
                """, secondStatus, executionIds.get(1));
    }

    private String batchStatus() {
        return jdbc.queryForObject(
                "SELECT BATCH_STATUS FROM EXECUTION_BATCH WHERE BATCH_ID = ?",
                String.class, batchId);
    }

    private String requestStatus() {
        return jdbc.queryForObject(
                "SELECT REQUEST_STATUS FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class, requestId);
    }
}