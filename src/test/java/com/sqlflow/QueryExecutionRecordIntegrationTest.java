package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.service.ExpiredResultCleanupService;
import com.sqlflow.execution.service.ExpiredResultCleanupService.CleanupResult;
import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;
import com.sqlflow.execution.service.QueryExecutionRecordService;

/**
 * 실제 Oracle에서 실행 상태와 결과 정보 저장을 확인한다.
 *
 * 테스트에 @Transactional을 붙이지 않아 서비스 자체의 커밋·롤백을 확인한다.
 * 테스트용 데이터는 각 테스트 종료 후 외래키 역순으로 삭제한다.
 */
@SpringBootTest(properties = {
        "app.execution.query-timeout-seconds=60",
        "app.execution.max-file-bytes=104857600"
})

class QueryExecutionRecordIntegrationTest {
	
	@TempDir
	static Path cleanupStorageRoot;

	@DynamicPropertySource
	static void cleanupStorageProperties(DynamicPropertyRegistry registry) {
	    registry.add(
	            "sqlflow.result.storage-root",
	            () -> cleanupStorageRoot.toString());
	}

	@Autowired
	private ExpiredResultCleanupService cleanupService;

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;

    @Autowired
    private QueryExecutionRecordService service;
    
    @Autowired
    private QueryResultMapper queryResultMapper;
    
    @Autowired
    @Qualifier("managementTransactionManager")
    private PlatformTransactionManager managementTransactionManager;

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
    
    @Test
    void rechecksExpiryPathAndDeletionInsideTransaction() {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        var transaction =
                new TransactionTemplate(managementTransactionManager);

        transaction.executeWithoutResult(status -> {
            // 1. 아직 만료되지 않았다면 정리 대상이 아니다.
            setExpiry(resultId, cutoff.plusDays(1));

            assertNull(queryResultMapper.selectExpiredResultForUpdate(
                    resultId,
                    cleanupTestFilePath(),
                    cutoff));

            // 2. 만료됐어도 파일 경로가 다르면 정리 대상이 아니다.
            setExpiry(resultId, cutoff.minusDays(1));

            assertNull(queryResultMapper.selectExpiredResultForUpdate(
                    resultId,
                    "different.csv",
                    cutoff));

            // 3. 만료·경로 조건이 일치하면 잠금 조회에 성공한다.
            var target = queryResultMapper.selectExpiredResultForUpdate(
                    resultId,
                    cleanupTestFilePath(),
                    cutoff);

            assertNotNull(target);
            assertEquals(resultId, target.getResultId());
            assertEquals(cleanupTestFilePath(), target.getFilePath());

            // 4. 같은 트랜잭션에서 삭제 완료를 기록한다.
            assertEquals(
                    1,
                    queryResultMapper.markExpiredResultDeleted(
                            resultId,
                            cleanupTestFilePath(),
                            cutoff));

            // 5. 삭제 완료된 결과는 다시 정리 대상으로 반환하지 않는다.
            assertNull(queryResultMapper.selectExpiredResultForUpdate(
                    resultId,
                    cleanupTestFilePath(),
                    cutoff));
        });
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
    
    private Long createCleanupTestResult() {
        service.start(executionId);

        service.complete(
                executionId,
                new ExecutionResult(
                        "query-cleanup-" + executionId + ".csv",
                        4L,
                        512L,
                        120L));

        return jdbc.queryForObject("""
                SELECT RESULT_ID
                FROM QUERY_RESULT
                WHERE EXECUTION_ID = ?
                """, Long.class, executionId);
    }

    private String cleanupTestFilePath() {
        return "query-cleanup-" + executionId + ".csv";
    }

    private void setExpiry(Long resultId, LocalDateTime expiresAt) {
        jdbc.update("""
                UPDATE QUERY_RESULT
                SET CREATED_AT = ?,
                    EXPIRES_AT = ?
                WHERE RESULT_ID = ?
                """,
                Timestamp.valueOf(expiresAt.minusDays(30)),
                Timestamp.valueOf(expiresAt),
                resultId);
    }

    @Test
    void selectsResultAtExpiryBoundaryAndMarksDeletedOnce() {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        assertNotNull(cutoff);
        setExpiry(resultId, cutoff);

        var targets = queryResultMapper.selectExpiredResults(
                resultId - 1, cutoff, 1);

        assertEquals(1, targets.size());
        assertEquals(resultId, targets.get(0).getResultId());
        assertEquals(cleanupTestFilePath(), targets.get(0).getFilePath());

        assertEquals(
                1,
                queryResultMapper.markExpiredResultDeleted(
                        resultId, cleanupTestFilePath(), cutoff));

        Timestamp deletedAt = jdbc.queryForObject("""
                SELECT DELETED_AT
                FROM QUERY_RESULT
                WHERE RESULT_ID = ?
                """, Timestamp.class, resultId);

        assertNotNull(deletedAt);

        assertEquals(
                0,
                queryResultMapper.markExpiredResultDeleted(
                        resultId, cleanupTestFilePath(), cutoff));

        assertEquals(
                deletedAt,
                jdbc.queryForObject("""
                        SELECT DELETED_AT
                        FROM QUERY_RESULT
                        WHERE RESULT_ID = ?
                        """, Timestamp.class, resultId));

        assertEquals("SUCCESS", executionStatus());
        assertEquals(1, resultCount());
    }

    @Test
    void doesNotSelectOrMarkUnexpiredResult() {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.plusDays(1));

        var targets = queryResultMapper.selectExpiredResults(
                resultId - 1, cutoff, 1);

        assertTrue(targets.stream()
                .noneMatch(item -> resultId.equals(item.getResultId())));

        assertEquals(
                0,
                queryResultMapper.markExpiredResultDeleted(
                        resultId, cleanupTestFilePath(), cutoff));
    }

    @Test
    void excludesAlreadyDeletedResult() {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.minusDays(1));

        assertEquals(
                1,
                queryResultMapper.markExpiredResultDeleted(
                        resultId, cleanupTestFilePath(), cutoff));

        var targets = queryResultMapper.selectExpiredResults(
                resultId - 1, cutoff, 1);

        assertTrue(targets.stream()
                .noneMatch(item -> resultId.equals(item.getResultId())));
    }

    @Test
    void requiresMatchingPathAndAdvancesByResultId() {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.minusDays(1));

        assertEquals(
                0,
                queryResultMapper.markExpiredResultDeleted(
                        resultId, "different.csv", cutoff));

        var targets = queryResultMapper.selectExpiredResults(
                resultId - 1, cutoff, 1);

        assertEquals(1, targets.size());
        assertEquals(resultId, targets.get(0).getResultId());

        var nextTargets = queryResultMapper.selectExpiredResults(
                resultId, cutoff, 1);

        assertTrue(nextTargets.stream()
                .allMatch(item -> item.getResultId() > resultId));
    }
    
    @Test
    void deletesActualCsvAndRecordsDeletion() throws Exception {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.minusDays(1));

        Path file = cleanupStorageRoot.resolve(cleanupTestFilePath());
        Files.writeString(file, "id\r\n1\r\n");

        assertEquals(
                CleanupResult.DELETED,
                cleanupService.cleanupOne(
                        resultId,
                        cleanupTestFilePath(),
                        cutoff));

        assertFalse(Files.exists(file));
        assertNotNull(deletionTime(resultId));

        // 파일 삭제와 별개로 실행 이력과 결과 메타데이터는 유지한다.
        assertEquals("SUCCESS", executionStatus());
        assertEquals(1, resultCount());

        // 이미 처리한 결과는 다시 삭제하지 않는다.
        assertEquals(
                CleanupResult.SKIPPED,
                cleanupService.cleanupOne(
                        resultId,
                        cleanupTestFilePath(),
                        cutoff));
    }

    @Test
    void missingActualCsvStillRecordsDeletion() throws Exception {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.minusDays(1));

        Path file = cleanupStorageRoot.resolve(cleanupTestFilePath());
        assertFalse(Files.exists(file));

        assertEquals(
                CleanupResult.ALREADY_MISSING,
                cleanupService.cleanupOne(
                        resultId,
                        cleanupTestFilePath(),
                        cutoff));

        assertNotNull(deletionTime(resultId));
    }

    @Test
    void unexpiredActualCsvIsPreserved() throws Exception {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.plusDays(1));

        Path file = cleanupStorageRoot.resolve(cleanupTestFilePath());
        Files.writeString(file, "keep");

        assertEquals(
                CleanupResult.SKIPPED,
                cleanupService.cleanupOne(
                        resultId,
                        cleanupTestFilePath(),
                        cutoff));

        assertEquals("keep", Files.readString(file));
        assertNull(deletionTime(resultId));
    }

    @Test
    void invalidFileTypeDoesNotRecordDeletion() throws Exception {
        Long resultId = createCleanupTestResult();
        LocalDateTime cutoff = queryResultMapper.selectCleanupCutoff();

        setExpiry(resultId, cutoff.minusDays(1));

        // CSV 파일 대신 같은 이름의 디렉터리를 만들어 삭제 오류를 유발한다.
        Path directory = Files.createDirectory(
                cleanupStorageRoot.resolve(cleanupTestFilePath()));

        assertThrows(
                IOException.class,
                () -> cleanupService.cleanupOne(
                        resultId,
                        cleanupTestFilePath(),
                        cutoff));

        assertTrue(Files.isDirectory(directory));
        assertNull(deletionTime(resultId));
        assertEquals("SUCCESS", executionStatus());
    }

    private Timestamp deletionTime(Long resultId) {
        return jdbc.queryForObject("""
                SELECT DELETED_AT
                FROM QUERY_RESULT
                WHERE RESULT_ID = ?
                """, Timestamp.class, resultId);
    }
}