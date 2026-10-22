package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;

/**
 * 실행 API → 재검증 → 실제 MySQL 조회 → CSV 저장 → Oracle 결과 기록을 확인한다.
 *
 * 비동기 작업이 데이터를 읽어야 하므로 테스트에 @Transactional을 붙이지 않는다.
 * 기존 업무 데이터·정책·역할은 변경하지 않는다.
 */
@SpringBootTest(properties = {
        "app.execution.query-timeout-seconds=15",
        "app.execution.max-file-bytes=1048576"
})
@AutoConfigureMockMvc
class ExecutionFlowIntegrationTest {

    private static final String FIRST_SQL = """
            SELECT department_id, department_name
            FROM sqlflow_demo.demo_department
            ORDER BY department_id
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("managementDataSource")
    private DataSource managementDataSource;

    @Autowired
    @Qualifier("sqlExecutionExecutor")
    private ThreadPoolTaskExecutor executor;

    @Value("${sqlflow.result.storage-root}")
    private String storageRoot;

    private JdbcTemplate jdbc;
    private SqlFlowUserDetails requester;
    private SqlFlowUserDetails approver;

    private Long requesterId;
    private Long requestId;
    private Long batchId;
    private Long deptId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(managementDataSource);

        // 실행 권한은 Mock 역할만이 아니라 실제 USER_ROLE에서도 확인된다.
        List<Map<String, Object>> approvers = jdbc.queryForList("""
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

        assertFalse(approvers.isEmpty(), "활성 DB_APPROVER 계정이 필요합니다.");

        long approverId =
                ((Number) approvers.get(0).get("USER_ID")).longValue();
        long approverDeptId =
                ((Number) approvers.get(0).get("DEPT_ID")).longValue();

        approver = loginUser(
                approverId, approverDeptId, List.of("DB_APPROVER"));

        // demo_department에 GENERAL 정책이 정확히 하나 있는 부서를 선택한다.
        List<Long> departments = jdbc.queryForList("""
                SELECT P.DEPT_ID
                FROM DATA_RESOURCE R
                JOIN DATA_ACCESS_POLICY P
                  ON P.RESOURCE_ID = R.RESOURCE_ID
                WHERE R.SCHEMA_NAME = 'sqlflow_demo'
                  AND R.OBJECT_NAME = 'demo_department'
                  AND R.ENABLED = 'Y'
                  AND P.ENABLED = 'Y'
                GROUP BY P.DEPT_ID
                HAVING COUNT(*) = 1
                   AND MIN(P.ACCESS_LEVEL) = 'GENERAL'
                ORDER BY P.DEPT_ID
                FETCH FIRST 1 ROW ONLY
                """, Long.class);

        assertFalse(
                departments.isEmpty(),
                "demo_department에 GENERAL 접근 정책이 있는 부서가 필요합니다.");

        deptId = departments.get(0);

        long candidateId =
                ThreadLocalRandom.current().nextLong(8_000_000_000L, 9_000_000_000L);

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM USERS WHERE USER_ID = ?",
                Integer.class, candidateId));

        String loginId = "exec-it-" + UUID.randomUUID()
                .toString().replace("-", "").substring(0, 12);

        // 필수 비밀번호·직급 값은 기존 계정에서 복사한다.
        // 실제 로그인 절차는 이 테스트의 대상이 아니다.
        int inserted = jdbc.update("""
                INSERT INTO USERS (
                    USER_ID, DEPT_ID, POSITION_ID,
                    LOGIN_ID, PASSWORD_HASH, USER_NAME, ENABLED, CREATED_AT
                )
                SELECT ?, ?, POSITION_ID,
                       ?, PASSWORD_HASH, ?, 'Y', SYSTIMESTAMP
                FROM USERS
                WHERE USER_ID = ?
                """,
                candidateId, deptId, loginId,
                "실행 통합 테스트", approverId);

        assertEquals(1, inserted);

        // INSERT 성공 후에만 정리 대상으로 등록한다.
        requesterId = candidateId;
        requester = loginUser(requesterId, deptId, List.of());
    }

    @Test
    void allSqlsSucceedAndCreateSharedResultFiles() throws Exception {
        createApprovedRequest("""
                SELECT COUNT(*) AS total_count
                FROM sqlflow_demo.demo_department
                """);

        registerBatch();

        // 일반 요청자는 실행할 수 없다.
        mockMvc.perform(post(runUrl())
                        .with(user(requester))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        startBatch();

        String result = awaitCompletion();

        assertEquals("SUCCESS", JsonPath.read(result, "$.batchStatus"));
        assertEquals("COMPLETED", JsonPath.read(result, "$.requestStatus"));
        assertEquals(2, number(result, "$.successCount"));
        assertEquals(0, number(result, "$.failureCount"));

        assertResultFiles(2);

        // 완료된 묶음을 다시 실행할 수 없다.
        mockMvc.perform(post(runUrl())
                        .with(user(approver))
                        .with(csrf()))
                .andExpect(status().isConflict());

        assertResultFiles(2);

        // 현재 DB_APPROVER도 상태를 조회할 수 있다.
        mockMvc.perform(get(statusUrl()).with(user(approver)))
                .andExpect(status().isOk());
    }

    @Test
    void validationFailureProducesPartialFailure() throws Exception {
        createApprovedRequest("""
                SELECT missing_column_for_execution_test
                FROM sqlflow_demo.demo_department
                """);

        registerBatch();
        startBatch();

        String result = awaitCompletion();

        assertEquals("PARTIAL_FAILED", JsonPath.read(result, "$.batchStatus"));
        assertEquals("PARTIAL_FAILED", JsonPath.read(result, "$.requestStatus"));
        assertEquals(1, number(result, "$.successCount"));
        assertEquals(1, number(result, "$.failureCount"));

        assertResultFiles(1);

        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                  AND STATUS = 'FAILED'
                  AND ERROR_CODE = 'REVALIDATION_FAILED'
                """, Integer.class, batchId));
    }

    private void createApprovedRequest(String secondSql) {
        requestId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL", Long.class);

        jdbc.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE, REQUEST_STATUS, APPROVAL_STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, 'READY', 'APPROVED')
                """,
                requestId, "TEST-FLOW-" + requestId,
                requesterId, deptId,
                "실행 흐름 통합 테스트", "실제 실행과 저장 연결 확인");

        List<String> sqls = List.of(FIRST_SQL, secondSql);

        for (int index = 0; index < sqls.size(); index++) {
            Long queryId = jdbc.queryForObject(
                    "SELECT SEQ_QUERY_ITEM.NEXTVAL FROM DUAL", Long.class);

            jdbc.update("""
                    INSERT INTO QUERY_ITEM (
                        QUERY_ID, REQUEST_ID, QUERY_ORDER,
                        QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                    ) VALUES (?, ?, ?, ?, ?, 'MANUAL')
                    """,
                    queryId, requestId, index + 1,
                    "테스트 SQL " + (index + 1), sqls.get(index));
        }
    }

    private void registerBatch() throws Exception {
        String response = mockMvc.perform(post(
                        "/api/executions/requests/{requestId}/batches", requestId)
                        .with(user(approver))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        batchId = ((Number) JsonPath.read(response, "$.batchId")).longValue();
    }

    private void startBatch() throws Exception {
        mockMvc.perform(post(runUrl())
                        .with(user(approver))
                        .with(csrf()))
                .andExpect(status().isAccepted());
    }

    /** HTTP 응답을 기다리는 대신 상태 API를 반복 조회한다. */
    private String awaitCompletion() throws Exception {
        long deadline = System.nanoTime() + 60_000_000_000L;
        String lastResponse = "";

        while (System.nanoTime() < deadline) {
            lastResponse = mockMvc.perform(
                            get(statusUrl()).with(user(requester)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String state = JsonPath.read(lastResponse, "$.batchStatus");

            if (List.of("SUCCESS", "FAILED", "PARTIAL_FAILED").contains(state)) {
                return lastResponse;
            }

            Thread.sleep(100);
        }

        throw new AssertionError(
                "60초 안에 실행이 완료되지 않았습니다. 마지막 상태: " + lastResponse);
    }

    private void assertResultFiles(int expectedCount) throws Exception {
        List<String> paths = resultPaths();

        assertEquals(expectedCount, paths.size());

        for (String relativePath : paths) {
            Path file = resolveResultFile(relativePath);

            assertFalse(Path.of(relativePath).isAbsolute());
            assertTrue(Files.isRegularFile(file), "결과 파일이 없습니다: " + file);
            assertTrue(Files.size(file) > 3, "BOM 외의 CSV 내용이 필요합니다.");
        }
    }

    private List<String> resultPaths() {
        return jdbc.queryForList("""
                SELECT R.FILE_PATH
                FROM QUERY_RESULT R
                JOIN QUERY_EXECUTION E ON E.EXECUTION_ID = R.EXECUTION_ID
                JOIN EXECUTION_BATCH B ON B.BATCH_ID = E.BATCH_ID
                WHERE B.REQUEST_ID = ?
                """, String.class, requestId);
    }

    private Path resolveResultFile(String relativePath) {
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        Path relative = Path.of(relativePath);

        assertFalse(relative.isAbsolute());

        Path file = root.resolve(relative).normalize();

        assertTrue(file.startsWith(root) && !file.equals(root));

        return file;
    }

    private String runUrl() {
        return "/api/executions/batches/" + batchId + "/run";
    }

    private String statusUrl() {
        return "/api/executions/batches/" + batchId;
    }

    private int number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).intValue();
    }

    private SqlFlowUserDetails loginUser(
            long userId, long departmentId, List<String> roles) {

        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setDeptId(departmentId);
        account.setLoginId("execution-test-" + userId);
        account.setUserName("실행 테스트");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(account, roles);
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (jdbc == null) {
            return;
        }

        // 작업 중인 데이터를 삭제하지 않도록 백그라운드 작업 종료를 기다린다.
        long deadline = System.nanoTime() + 20_000_000_000L;

        while (executor.getActiveCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(100);
        }

        assertEquals(
                0, executor.getActiveCount(),
                "작업이 실행 중이어서 테스트 데이터를 보존합니다. 관리자 확인이 필요합니다.");

        if (requestId != null) {
            for (String relativePath : resultPaths()) {
                Files.deleteIfExists(resolveResultFile(relativePath));
            }

            jdbc.update("""
                    DELETE FROM QUERY_RESULT
                    WHERE EXECUTION_ID IN (
                        SELECT E.EXECUTION_ID
                        FROM QUERY_EXECUTION E
                        JOIN EXECUTION_BATCH B ON B.BATCH_ID = E.BATCH_ID
                        WHERE B.REQUEST_ID = ?
                    )
                    """, requestId);

            jdbc.update("""
                    DELETE FROM QUERY_EXECUTION
                    WHERE BATCH_ID IN (
                        SELECT BATCH_ID
                        FROM EXECUTION_BATCH
                        WHERE REQUEST_ID = ?
                    )
                    """, requestId);

            jdbc.update(
                    "DELETE FROM EXECUTION_BATCH WHERE REQUEST_ID = ?", requestId);
            jdbc.update(
                    "DELETE FROM QUERY_ITEM WHERE REQUEST_ID = ?", requestId);
            jdbc.update(
                    "DELETE FROM QUERY_REQUEST WHERE REQUEST_ID = ?", requestId);
        }

        if (requesterId != null) {
            jdbc.update("DELETE FROM USERS WHERE USER_ID = ?", requesterId);
        }
    }
}