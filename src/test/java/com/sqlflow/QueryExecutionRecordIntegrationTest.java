package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;
import com.sqlflow.execution.service.QueryExecutionRecordService;

/**
 * 실제 Oracle에서 실행 상태와 결과 정보 저장을 확인한다.
 *
 * 테스트에 @Transactional을 붙이지 않아 서비스 자체의 커밋·롤백을 확인한다.
 * 테스트용 데이터는 각 테스트 종료 후 외래키 역순으로 삭제한다.
 */
@SpringBootTest(properties = {
		"sqlflow.result.storage-root=${java.io.tmpdir}/sqlflow-test-results",
        "app.execution.query-timeout-seconds=60",
        "app.execution.max-file-bytes=104857600"
})
class QueryExecutionRecordIntegrationTest {

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;

    @Autowired
    private QueryExecutionRecordService service;

    private JdbcTemplate jdbc;
    private Long requestId;
    private Long queryId;
    private Long batchId;
    private Long executionId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(managementDataSource);

        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT USER_ID, DEPT_ID
                FROM USERS
                WHERE DEPT_ID IS NOT NULL
                ORDER BY USER_ID
                FETCH FIRST 1 ROW ONLY
                """);

        assertFalse(users.isEmpty(), "부서가 있는 테스트용 사용자가 필요합니다.");

        long userId = ((Number) users.get(0).get("USER_ID")).longValue();
        long deptId = ((Number) users.get(0).get("DEPT_ID")).longValue();

        requestId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE, REQUEST_STATUS, APPROVAL_STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, 'READY', 'APPROVED')
                """,
                requestId, "TEST-RESULT-" + requestId,
                userId, deptId, "결과 저장 테스트", "실행 기록 트랜잭션 확인");

        queryId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_ITEM.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID, REQUEST_ID, QUERY_ORDER,
                    QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                ) VALUES (?, ?, 1, ?, ?, 'MANUAL')
                """,
                queryId, requestId, "테스트 SQL",
                "SELECT department_id FROM demo_department");

        batchId = jdbc.queryForObject(
                "SELECT SEQ_EXECUTION_BATCH.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO EXECUTION_BATCH (
                    BATCH_ID, REQUEST_ID, INITIATED_BY,
                    TRIGGER_TYPE, BATCH_TYPE, BATCH_STATUS, REQUESTED_AT
                ) VALUES (?, ?, ?, 'MANUAL', 'INITIAL', 'PENDING', SYSTIMESTAMP)
                """,
                batchId, requestId, userId);

        executionId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_EXECUTION.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_EXECUTION (
                    EXECUTION_ID, BATCH_ID, QUERY_ID, ATTEMPT_NO, STATUS
                ) VALUES (?, ?, ?, 1, 'PENDING')
                """,
                executionId, batchId, queryId);
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null) {
            return;
        }

        if (executionId != null) {
            jdbc.update(
                    "DELETE FROM QUERY_RESULT WHERE EXECUTION_ID = ?",
                    executionId);
            jdbc.update(
                    "DELETE FROM QUERY_EXECUTION WHERE EXECUTION_ID = ?",
                    executionId);
        }

        if (batchId != null) {
            jdbc.update("DELETE FROM EXECUTION_BATCH WHERE BATCH_ID = ?", batchId);
        }

        if (queryId != null) {
            jdbc.update("DELETE FROM QUERY_ITEM WHERE QUERY_ID = ?", queryId);
        }

        if (requestId != null) {
            jdbc.update("DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?", requestId);
        }
    }

    @Test
    void savesResultAndSuccessStatus() {
        service.start(executionId);

        assertEquals("RUNNING", executionStatus());

        ExecutionResult result = new ExecutionResult(
                "query-integration.csv", 4L, 512L, 120L);

        service.complete(executionId, result);

        Map<String, Object> execution = jdbc.queryForMap("""
                SELECT STATUS, STARTED_AT, FINISHED_AT, DURATION_MS,
                       ERROR_CODE, ERROR_MESSAGE
                FROM QUERY_EXECUTION
                WHERE EXECUTION_ID = ?
                """, executionId);

        assertEquals("SUCCESS", execution.get("STATUS"));
        assertNotNull(execution.get("STARTED_AT"));
        assertNotNull(execution.get("FINISHED_AT"));
        assertEquals(120L, ((Number) execution.get("DURATION_MS")).longValue());
        assertNull(execution.get("ERROR_CODE"));
        assertNull(execution.get("ERROR_MESSAGE"));

        Map<String, Object> saved = jdbc.queryForMap("""
                SELECT FILE_PATH, ROW_COUNT, FILE_SIZE,
                       CREATED_AT, EXPIRES_AT, DELETED_AT,
                       CASE
                           WHEN EXPIRES_AT = CREATED_AT + INTERVAL '30' DAY
                           THEN 1 ELSE 0
                       END AS VALID_EXPIRY
                FROM QUERY_RESULT
                WHERE EXECUTION_ID = ?
                """, executionId);

        assertEquals("query-integration.csv", saved.get("FILE_PATH"));
        assertEquals(4L, ((Number) saved.get("ROW_COUNT")).longValue());
        assertEquals(512L, ((Number) saved.get("FILE_SIZE")).longValue());
        assertNotNull(saved.get("CREATED_AT"));
        assertNotNull(saved.get("EXPIRES_AT"));
        assertNull(saved.get("DELETED_AT"));
        assertEquals(1, ((Number) saved.get("VALID_EXPIRY")).intValue());
    }

    @Test
    void savesFailureWithoutResult() {
        service.start(executionId);
        service.fail(executionId, 200L, "MYSQL_1054", "존재하지 않는 컬럼");

        Map<String, Object> execution = jdbc.queryForMap("""
                SELECT STATUS, DURATION_MS, ERROR_CODE, FINISHED_AT,
                       DBMS_LOB.SUBSTR(ERROR_MESSAGE, 1000, 1) AS ERROR_TEXT
                FROM QUERY_EXECUTION
                WHERE EXECUTION_ID = ?
                """, executionId);

        assertEquals("FAILED", execution.get("STATUS"));
        assertEquals(200L, ((Number) execution.get("DURATION_MS")).longValue());
        assertEquals("MYSQL_1054", execution.get("ERROR_CODE"));
        assertEquals("존재하지 않는 컬럼", execution.get("ERROR_TEXT"));
        assertNotNull(execution.get("FINISHED_AT"));
        assertEquals(0, resultCount());
    }

    @Test
    void resultInsertFailureRollsBackSuccessStatus() {
        service.start(executionId);

        // FILE_PATH VARCHAR2(500) 한도를 초과해 실제 INSERT 실패를 발생시킨다.
        ExecutionResult invalidResult = new ExecutionResult(
                "x".repeat(501), 4L, 512L, 120L);

        assertThrows(
                DataAccessException.class,
                () -> service.complete(executionId, invalidResult));

        // complete() 안의 SUCCESS 변경도 함께 롤백되어야 한다.
        assertEquals("RUNNING", executionStatus());
        assertEquals(0, resultCount());

        assertNull(jdbc.queryForObject("""
                SELECT FINISHED_AT
                FROM QUERY_EXECUTION
                WHERE EXECUTION_ID = ?
                """, java.sql.Timestamp.class, executionId));

        // 롤백 이후 별도 트랜잭션으로 실패 기록이 가능한지도 확인한다.
        service.fail(executionId, 120L, "RESULT_SAVE_FAILED", "결과 정보 저장 실패");

        assertEquals("FAILED", executionStatus());
    }

    private String executionStatus() {
        return jdbc.queryForObject(
                "SELECT STATUS FROM QUERY_EXECUTION WHERE EXECUTION_ID = ?",
                String.class, executionId);
    }

    private int resultCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM QUERY_RESULT WHERE EXECUTION_ID = ?",
                Integer.class, executionId);
    }
}