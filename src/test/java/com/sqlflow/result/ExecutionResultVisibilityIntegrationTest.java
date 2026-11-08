package com.sqlflow.result;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.domain.QueryResult;
import com.sqlflow.execution.mapper.ExecutionResultMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.mapper.QueryResultMapper;

@SpringBootTest
@Transactional(transactionManager = "managementTransactionManager")
class ExecutionResultVisibilityIntegrationTest {

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;
    
    @Autowired
    @Qualifier("managementSqlSessionFactory")
    private SqlSessionFactory managementSqlSessionFactory;
    
    @Autowired
    private ExecutionResultMapper resultMapper;

    @Autowired
    private QueryExecutionMapper executionMapper;

    @Autowired
    private QueryResultMapper queryResultMapper;

    private JdbcTemplate jdbc;

    private Long requestDeptId;
    private Long otherDeptId;
    private Long dbDeptId;

    private Long drafterId;
    private Long sameDeptUserId;
    private Long otherDeptUserId;
    private Long dbTeamUserId;

    private Long positionId;
    private Long requestId;
    private Long executionId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(managementDataSource);

        List<Long> ordinaryDepartments = jdbc.queryForList("""
                SELECT DEPT_ID
                FROM DEPARTMENT
                WHERE DEPT_NAME <> 'DB팀'
                ORDER BY DEPT_ID
                """, Long.class);

        assertTrue(
                ordinaryDepartments.size() >= 2,
                "테스트 DB에 DB팀 이외의 부서가 두 개 이상 필요합니다.");

        List<Long> dbDepartments = jdbc.queryForList("""
                SELECT DEPT_ID
                FROM DEPARTMENT
                WHERE DEPT_NAME = 'DB팀'
                ORDER BY DEPT_ID
                """, Long.class);

        assertEquals(
                1, dbDepartments.size(),
                "테스트 DB에 DB팀 부서가 하나 있어야 합니다.");

        List<Long> positions = jdbc.queryForList("""
                SELECT POSITION_ID
                FROM POSITION
                ORDER BY POSITION_ID
                FETCH FIRST 1 ROW ONLY
                """, Long.class);

        assertFalse(positions.isEmpty(), "직급 데이터가 필요합니다.");

        requestDeptId = ordinaryDepartments.get(0);
        otherDeptId = ordinaryDepartments.get(1);
        dbDeptId = dbDepartments.get(0);
        positionId = positions.get(0);

        // 역할을 부여하지 않은 별도의 테스트 사용자들이다.
        drafterId = createUser(requestDeptId);
        sameDeptUserId = createUser(requestDeptId);
        otherDeptUserId = createUser(otherDeptId);
        dbTeamUserId = createUser(dbDeptId);
    }

    @Test
    void requesterCanReadOwnResult() {
        assertResultVisibility(drafterId, true);
    }

    @Test
    void sameDepartmentUserWithoutApprovalOrRolesCanReadResult() {
        assertFalse(
                resultMapper.isActiveDbTeamMember(sameDeptUserId));

        assertResultVisibility(sameDeptUserId, true);
    }

    @Test
    void otherDepartmentUserCannotReadResult() {
        assertResultVisibility(otherDeptUserId, false);
    }

    @Test
    void dbTeamUserWithoutRolesCanReadResult() {
        assertEquals(
                0,
                jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM USER_ROLE
                        WHERE USER_ID = ?
                        """, Integer.class, dbTeamUserId));

        assertTrue(
                resultMapper.isActiveDbTeamMember(dbTeamUserId));

        assertResultVisibility(dbTeamUserId, true);
    }

    @Test
    void dbRolesOutsideDbTeamDoNotGrantResultVisibility() {
        List<Map<String, Object>> roles = jdbc.queryForList("""
                SELECT ROLE_ID, ROLE_CODE
                FROM ROLE
                WHERE ROLE_CODE IN ('DB_ADMIN', 'DB_APPROVER')
                """);

        assertEquals(
                2, roles.size(),
                "DB_ADMIN·DB_APPROVER 역할 데이터가 필요합니다.");

        for (Map<String, Object> role : roles) {
            jdbc.update("""
                    INSERT INTO USER_ROLE (USER_ID, ROLE_ID)
                    VALUES (?, ?)
                    """, otherDeptUserId, role.get("ROLE_ID"));
        }

        assertFalse(
                resultMapper.isActiveDbTeamMember(otherDeptUserId));

        assertResultVisibility(otherDeptUserId, false);
    }

    @Test
    void disabledDbTeamUserCannotReadResult() {
        jdbc.update("""
                UPDATE USERS
                SET ENABLED = 'N'
                WHERE USER_ID = ?
                """, dbTeamUserId);

        assertFalse(
                resultMapper.isActiveDbTeamMember(dbTeamUserId));

        assertResultVisibility(dbTeamUserId, false);
    }

    @Test
    void movingOutOfDbTeamRemovesGlobalVisibility() {
        assertTrue(
                resultMapper.isActiveDbTeamMember(dbTeamUserId));

        assertEquals(1, jdbc.update("""
                UPDATE USERS
                SET DEPT_ID = ?
                WHERE USER_ID = ?
                """, otherDeptId, dbTeamUserId));

        // 실제 DB의 부서가 변경됐는지 먼저 확인한다.
        assertEquals(
                otherDeptId,
                jdbc.queryForObject("""
                        SELECT DEPT_ID
                        FROM USERS
                        WHERE USER_ID = ?
                        """, Long.class, dbTeamUserId));

        // JDBC 변경은 MyBatis의 세션 캐시를 자동으로 비우지 않는다.
        clearManagementSessionCache();

        assertFalse(
                resultMapper.isActiveDbTeamMember(dbTeamUserId));

        assertResultVisibility(dbTeamUserId, false);
    }

    @Test
    void drafterDepartmentChangeDoesNotMoveRequestVisibility() {
        // 요청 작성자가 다른 부서로 이동해도 기안 부서는 유지된다.
        jdbc.update("""
                UPDATE USERS
                SET DEPT_ID = ?
                WHERE USER_ID = ?
                """, otherDeptId, drafterId);

        assertResultVisibility(sameDeptUserId, true);

        assertNull(
                resultMapper.selectMyResultDetail(
                        executionId, otherDeptUserId));

        // 작성자 본인의 이력 열람 자격은 유지된다.
        assertNotNull(
                resultMapper.selectMyResultDetail(
                        executionId, drafterId));
    }

    private void assertResultVisibility(Long actorId, boolean allowed) {
        long beforeCount =
                resultMapper.countMyLatestResults(actorId, "", "");

        createResult();

        long afterCount =
                resultMapper.countMyLatestResults(actorId, "", "");

        assertEquals(
                beforeCount + (allowed ? 1 : 0),
                afterCount,
                "목록 건수에 동일한 접근 조건이 적용되어야 합니다.");

        // 방금 생성한 실행은 EXECUTION_ID 내림차순 목록의 최신 건이다.
        var rows =
                resultMapper.selectMyLatestResults(
                        actorId, 0L, 100, "", "");

        boolean listed = rows.stream().anyMatch(
                row -> executionId.equals(row.getExecutionId()));

        assertEquals(allowed, listed);

        var detail =
                resultMapper.selectMyResultDetail(executionId, actorId);

        var file =
                resultMapper.selectMyResultFile(executionId, actorId);

        if (allowed) {
            assertNotNull(detail);
            assertNotNull(file);
            assertNotNull(file.getResultId());
            assertEquals(requestId, detail.getRequestId());
        } else {
            assertNull(detail);
            assertNull(file);
        }
    }

    private Long createUser(Long deptId) {
        Long userId = jdbc.queryForObject(
                "SELECT SEQ_USERS.NEXTVAL FROM DUAL",
                Long.class);

        assertEquals(1, jdbc.update("""
                INSERT INTO USERS (
                    USER_ID, DEPT_ID, POSITION_ID,
                    LOGIN_ID, PASSWORD_HASH, USER_NAME, ENABLED
                ) VALUES (?, ?, ?, ?, ?, ?, 'Y')
                """,
                userId,
                deptId,
                positionId,
                "result-policy-test-" + userId,
                "not-used-for-login",
                "결과 권한 테스트"));

        return userId;
    }

    private void createResult() {
        requestId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL",
                Long.class);

        assertEquals(1, jdbc.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE,
                    REQUEST_STATUS, APPROVAL_STATUS, REQUEST_TYPE
                ) VALUES (?, ?, ?, ?, ?, ?, 'READY', 'APPROVED', 'SELECT')
                """,
                requestId,
                "RESULT-POLICY-" + requestId,
                drafterId,
                requestDeptId,
                "결과 접근 정책 테스트",
                "부서별 결과 조회 범위 확인"));

        Long queryId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_ITEM.NEXTVAL FROM DUAL",
                Long.class);

        assertEquals(1, jdbc.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID, REQUEST_ID, QUERY_ORDER,
                    QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                ) VALUES (?, ?, 1, ?, ?, 'MANUAL')
                """,
                queryId,
                requestId,
                "실행하지 않는 테스트 SQL",
                "SELECT 1"));

        QueryExecution execution = new QueryExecution();
        execution.setQueryId(queryId);
        execution.setBatchId(null);
        execution.setExecutedBy(dbTeamUserId);
        execution.setAttemptNo(1);

        assertEquals(1, executionMapper.insertExecution(execution));

        executionId = execution.getExecutionId();

        assertEquals(1, executionMapper.markRunning(executionId));

        execution.setStatus("SUCCESS");
        execution.setDurationMs(1L);

        assertEquals(1, executionMapper.finishExecution(execution));

        QueryResult result = new QueryResult();
        result.setExecutionId(executionId);
        result.setFilePath("result-policy-test-" + executionId + ".csv");
        result.setRowCount(1L);
        result.setFileSize(10L);

        assertEquals(1, queryResultMapper.insertResult(result));
    }
    
    private void clearManagementSessionCache() {
        SqlSession session =
                SqlSessionUtils.getSqlSession(managementSqlSessionFactory);

        try {
            session.clearCache();
        } finally {
            SqlSessionUtils.closeSqlSession(
                    session, managementSqlSessionFactory);
        }
    }
}