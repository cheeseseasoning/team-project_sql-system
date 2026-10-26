package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.ResultAccessLogService;

/**
 * 실제 Oracle 로그 저장·시퀀스·독립 커밋을 검증한다.
 *
 * 테스트 클래스에 @Transactional을 붙이지 않는다.
 * 테스트용 기안서는 먼저 커밋하여 REQUIRES_NEW에서도 참조할 수 있게 한다.
 * 생성한 로그와 기안서는 테스트 종료 시 삭제한다.
 * 실제 CSV나 MySQL 업무 데이터에는 접근하지 않는다.
 */
@SpringBootTest
class ResultAccessLogIntegrationTest {

    @Autowired
    private ResultAccessLogService service;

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;

    @Autowired
    @Qualifier("managementTransactionManager")
    private PlatformTransactionManager transactionManager;

    private JdbcTemplate jdbc;
    private TransactionTemplate independentTransaction;

    private Long userId;
    private Long deptId;
    private Long requestId;

    // 이 테스트 인스턴스에서 발급한 요청 ID만 정리에 사용한다.
    private final List<Long> createdRequestIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(managementDataSource);

        independentTransaction =
                new TransactionTemplate(transactionManager);

        independentTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT USER_ID, DEPT_ID
                FROM SQLFLOW.USERS
                WHERE USER_ID > 0
                  AND ENABLED = 'Y'
                  AND DEPT_ID IS NOT NULL
                ORDER BY USER_ID
                FETCH FIRST 1 ROW ONLY
                """);

        assertFalse(
                users.isEmpty(),
                "개발·테스트 DB에 부서가 있는 활성 사용자가 필요합니다.");

        userId = ((Number) users.get(0).get("USER_ID")).longValue();
        deptId = ((Number) users.get(0).get("DEPT_ID")).longValue();

        requestId = nextRequestId();
        createdRequestIds.add(requestId);

        // 로그의 FK 대상은 독립 트랜잭션 실행 전에 커밋돼 있어야 한다.
        independentTransaction.executeWithoutResult(status ->
                insertTestRequest(requestId, "접근 로그 통합 테스트"));
    }

    @AfterEach
    void cleanUp() {
        if (jdbc == null
                || independentTransaction == null
                || createdRequestIds.isEmpty()) {
            return;
        }

        independentTransaction.executeWithoutResult(status -> {
            // FK 역순으로, 이번 테스트가 생성한 요청의 데이터만 정리한다.
            for (Long createdRequestId : createdRequestIds) {
                jdbc.update("""
                        DELETE FROM SQLFLOW.RESULT_ACCESS_LOG
                        WHERE REQUEST_ID = ?
                        """, createdRequestId);
            }

            for (Long createdRequestId : createdRequestIds) {
                jdbc.update("""
                        DELETE FROM SQLFLOW.QUERY_REQUEST
                        WHERE REQUEST_ID = ?
                        """, createdRequestId);
            }
        });
    }

    @Test
    void savesActualLogWithTimestampAndNullableResultId() {
        service.record(
                requestId,
                null,
                userId,
                "PREVIEW",
                "PREPARED");

        List<Map<String, Object>> rows = logs();

        assertEquals(1, rows.size());

        Map<String, Object> saved = rows.get(0);

        assertTrue(((Number) saved.get("LOG_ID")).longValue() > 0);

        assertEquals(
                requestId.longValue(),
                ((Number) saved.get("REQUEST_ID")).longValue());

        assertEquals(
                userId.longValue(),
                ((Number) saved.get("USER_ID")).longValue());

        assertNull(saved.get("RESULT_ID"));
        assertEquals("PREVIEW", saved.get("ACTION_TYPE"));
        assertEquals("PREPARED", saved.get("ACCESS_RESULT"));
        assertNotNull(saved.get("ACCESSED_AT"));
    }

    @Test
    void generatesDifferentLogIdsAndStoresDeniedAndError() {
        service.record(
                requestId,
                null,
                userId,
                "PREVIEW_MORE",
                "DENIED");

        service.record(
                requestId,
                null,
                userId,
                "DOWNLOAD",
                "ERROR");

        List<Map<String, Object>> rows = logs();

        assertEquals(2, rows.size());

        List<Long> logIds = rows.stream()
                .map(row -> ((Number) row.get("LOG_ID")).longValue())
                .toList();

        assertTrue(logIds.stream().allMatch(id -> id > 0));
        assertEquals(2, new HashSet<>(logIds).size());

        // シーケンスが連番であることは要求しない。
        assertEquals("PREVIEW_MORE", rows.get(0).get("ACTION_TYPE"));
        assertEquals("DENIED", rows.get(0).get("ACCESS_RESULT"));

        assertEquals("DOWNLOAD", rows.get(1).get("ACTION_TYPE"));
        assertEquals("ERROR", rows.get(1).get("ACCESS_RESULT"));
    }

    @Test
    void logRemainsCommittedAfterOuterTransactionRollsBack() {
        Long probeRequestId = nextRequestId();
        createdRequestIds.add(probeRequestId);

        TransactionTemplate outerTransaction =
                new TransactionTemplate(transactionManager);

        outerTransaction.executeWithoutResult(status -> {
            // 외부 트랜잭션의 롤백 여부를 확인할 별도 테스트 행.
            // 로그가 참조하는 requestId 행은 수정하거나 잠그지 않는다.
            insertTestRequest(
                    probeRequestId,
                    "외부 트랜잭션 롤백 확인");

            service.record(
                    requestId,
                    null,
                    userId,
                    "PREVIEW",
                    "DENIED");

            status.setRollbackOnly();
        });

        Long probeCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM SQLFLOW.QUERY_REQUEST
                WHERE REQUEST_ID = ?
                """, Long.class, probeRequestId);

        assertEquals(0L, probeCount.longValue());

        // 외부 행은 롤백됐지만 REQUIRES_NEW 로그는 남아야 한다.
        List<Map<String, Object>> rows = logs();

        assertEquals(1, rows.size());
        assertEquals("PREVIEW", rows.get(0).get("ACTION_TYPE"));
        assertEquals("DENIED", rows.get(0).get("ACCESS_RESULT"));
    }

    @Test
    void actualForeignKeyFailureReturns503AndLeavesNoLog() {
        // 기존 데이터를 삭제하지 않고, 존재하지 않는 FK로 실패를 재현한다.
        Long missingResultId = Long.MAX_VALUE;

        Long existingCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM SQLFLOW.QUERY_RESULT
                WHERE RESULT_ID = ?
                """, Long.class, missingResultId);

        assertEquals(
                0L,
                existingCount.longValue(),
                "테스트용 RESULT_ID가 이미 존재합니다.");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.record(
                        requestId,
                        missingResultId,
                        userId,
                        "DOWNLOAD",
                        "PREPARED"));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        assertTrue(logs().isEmpty());
    }

    private Long nextRequestId() {
        return jdbc.queryForObject("""
                SELECT SQLFLOW.SEQ_QUERY_REQUEST.NEXTVAL
                FROM DUAL
                """, Long.class);
    }

    private void insertTestRequest(
            Long newRequestId,
            String title) {

        int inserted = jdbc.update("""
                INSERT INTO SQLFLOW.QUERY_REQUEST (
                    REQUEST_ID,
                    REQUEST_NO,
                    DRAFTER_ID,
                    DEPT_ID,
                    TITLE,
                    PURPOSE
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                newRequestId,
                "TEST-LOG-" + newRequestId,
                userId,
                deptId,
                title,
                "RESULT_ACCESS_LOG 저장 검증 전용");

        assertEquals(1, inserted);
    }

    private List<Map<String, Object>> logs() {
        return jdbc.queryForList("""
                SELECT LOG_ID,
                       REQUEST_ID,
                       RESULT_ID,
                       USER_ID,
                       ACTION_TYPE,
                       ACCESS_RESULT,
                       ACCESSED_AT
                FROM SQLFLOW.RESULT_ACCESS_LOG
                WHERE REQUEST_ID = ?
                ORDER BY LOG_ID
                """, requestId);
    }
}