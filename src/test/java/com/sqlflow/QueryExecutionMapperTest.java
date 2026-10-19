package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.domain.QueryExecutionTarget;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.ExecutionBatchService;

/**
 * 실행 이력 ID로 SQL 원문과 관련 상태를 정확히 조회하는지 검증한다.
 * 실제 SQL 원문을 실행하지 않으며, 테스트 데이터는 종료 후 롤백된다.
 */
@SpringBootTest
@Transactional
class QueryExecutionMapperTest {

    private static final String TEST_SQL = "SELECT '조회 대상 확인' AS MESSAGE FROM DUAL";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ExecutionBatchService executionBatchService;

    @Autowired
    private QueryExecutionMapper queryExecutionMapper;

    private Long userId;
    private Long deptId;

    @BeforeEach
    void findFixtureUser() {
        // 외래키 연결에 필요한 기존 사용자 정보만 읽는다.
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
    void executionTargetContainsStoredSqlAndRelatedStatuses() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);
        Long batchId = executionBatchService.createInitialBatch(requestId, createExecutor());

        Long executionId = jdbcTemplate.queryForObject("""
                SELECT EXECUTION_ID
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                  AND QUERY_ID = ?
                """, Long.class, batchId, queryId);

        // 검사 대상인 MyBatis 조회 메소드를 호출한다.
        QueryExecutionTarget target = queryExecutionMapper.selectExecutionTarget(executionId);

        assertNotNull(target);
        assertEquals(executionId, target.getExecutionId());
        assertEquals(batchId, target.getBatchId());
        assertEquals(requestId, target.getRequestId());
        assertEquals(queryId, target.getQueryId());

        assertEquals("PENDING", target.getExecutionStatus());
        assertEquals("PENDING", target.getBatchStatus());
        assertEquals("READY", target.getRequestStatus());
        assertEquals("APPROVED", target.getApprovalStatus());

        // Oracle CLOB에 저장한 SQL이 문자열로 정확히 조회되는지 확인한다.
        assertEquals(TEST_SQL, target.getSqlText());
    }

    @Test
    void missingExecutionReturnsNull() {
        // 정상 ID는 양수 시퀀스로 발급하므로 존재하지 않는 음수 ID로 조회한다.
        QueryExecutionTarget target = queryExecutionMapper.selectExecutionTarget(-1L);

        assertNull(target);
    }

    /** 실행 가능한 테스트용 요청을 생성한다. */
    private Long createReadyRequest() {
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
                "실행 대상 조회 테스트",
                "Mapper 조회 결과 확인",
                "READY",
                "APPROVED");

        return requestId;
    }

    /** 요청에 SQL 한 개를 저장하고, 확인에 사용할 SQL 항목 ID를 반환한다. */
    private Long createQueryItem(Long requestId) {
        Long queryId = jdbcTemplate.queryForObject(
                "SELECT SEQ_QUERY_ITEM.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID, REQUEST_ID, QUERY_ORDER,
                    QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                queryId,
                requestId,
                1,
                "조회 대상 확인",
                TEST_SQL,
                "MANUAL");

        return queryId;
    }

    /** Service의 등록 권한 검사를 통과할 테스트용 로그인 정보를 만든다. */
    private SqlFlowUserDetails createExecutor() {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setDeptId(deptId);
        account.setLoginId("execution-target-test-user");
        account.setUserName("실행 대상 테스트 사용자");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(account, List.of("DB_APPROVER"));
    }
}