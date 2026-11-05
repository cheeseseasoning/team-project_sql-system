package com.sqlflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.domain.QueryExecutionTarget;
import com.sqlflow.execution.domain.QueryResult;
import com.sqlflow.execution.mapper.ExecutionQueueMapper;
import com.sqlflow.execution.mapper.ExecutionResultMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;
import com.sqlflow.execution.service.ExecutionBatchService;

/**
 * 실행 이력 ID로 SQL 원문과 관련 상태를 정확히 조회하는지 검증한다.
 * 실제 SQL 원문을 실행하지 않으며, 테스트 데이터는 종료 후 롤백된다.
 */
@SpringBootTest
@Transactional
class QueryExecutionMapperTest {
	
	@Autowired
	private ExecutionResultMapper executionResultMapper;

	@Autowired
	private QueryResultMapper queryResultMapper;

	@Autowired
	private ResultAccessLogMapper resultAccessLogMapper;
	
	@Autowired
	private ExecutionQueueMapper executionQueueMapper;

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
        // 실행 서비스는 로그인 역할과 함께 Oracle에 등록된 현재 권한도 확인한다.
        List<Map<String, Object>> users = jdbcTemplate.queryForList("""
                SELECT U.USER_ID, U.DEPT_ID
                FROM USERS U
                WHERE U.DEPT_ID IS NOT NULL
                  AND U.ENABLED = 'Y'
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

        assertFalse(users.isEmpty(), "테스트 DB에 부서가 지정된 활성 DB_APPROVER 계정이 필요합니다.");

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
    
    @Test
    void 배치가_없는_실행도_SQL과_기안서를_조회한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        QueryExecution execution = new QueryExecution();
        execution.setBatchId(null);
        execution.setQueryId(queryId);
        execution.setExecutedBy(userId);
        execution.setAttemptNo(1);
        execution.setRetryOfExecutionId(null);

        assertEquals(1, queryExecutionMapper.insertExecution(execution));

        QueryExecutionTarget target =
                queryExecutionMapper.selectExecutionTarget(
                        execution.getExecutionId());

        assertNotNull(target);
        assertEquals(execution.getExecutionId(), target.getExecutionId());
        assertEquals(requestId, target.getRequestId());
        assertEquals(queryId, target.getQueryId());
        assertEquals(userId, target.getDrafterId());

        assertNull(target.getBatchId());
        assertNull(target.getBatchStatus());

        assertEquals("PENDING", target.getExecutionStatus());
        assertEquals("READY", target.getRequestStatus());
        assertEquals("APPROVED", target.getApprovalStatus());
        assertEquals(TEST_SQL, target.getSqlText());
    }

    @Test
    void 배치와_SQL의_기안서가_다르면_조회하지_않는다() {
        // 첫 번째 기안서와 그 배치를 생성한다.
        Long firstRequestId = createReadyRequest();
        createQueryItem(firstRequestId);

        Long batchId = executionBatchService.createInitialBatch(
                firstRequestId, createExecutor());

        // 다른 기안서의 SQL을 생성한다.
        Long secondRequestId = createReadyRequest();
        Long secondQueryId = createQueryItem(secondRequestId);

        // 서로 다른 기안서의 배치와 SQL을 연결한 잘못된 이력.
        QueryExecution execution = new QueryExecution();
        execution.setBatchId(batchId);
        execution.setQueryId(secondQueryId);
        execution.setExecutedBy(userId);
        execution.setAttemptNo(1);
        execution.setRetryOfExecutionId(null);

        assertEquals(1, queryExecutionMapper.insertExecution(execution));

        QueryExecutionTarget target =
                queryExecutionMapper.selectExecutionTarget(
                        execution.getExecutionId());

        assertNull(target);
    }
    
    @Test
    void 배치_없는_결과도_목록과_상세와_파일과_접근로그에서_조회한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        QueryExecution execution = new QueryExecution();
        execution.setQueryId(queryId);
        execution.setBatchId(null);
        execution.setExecutedBy(userId);
        execution.setAttemptNo(1);

        queryExecutionMapper.insertExecution(execution);

        assertEquals(
                1,
                queryExecutionMapper.markRunning(execution.getExecutionId()));

        execution.setStatus("SUCCESS");
        execution.setDurationMs(10L);

        assertEquals(
                1,
                queryExecutionMapper.finishExecution(execution));

        // 파일 정보만 저장하며 실제 CSV 파일은 생성하지 않는다.
        QueryResult result = new QueryResult();
        result.setExecutionId(execution.getExecutionId());
        result.setFilePath("standalone-mapper-test.csv");
        result.setRowCount(3L);
        result.setFileSize(100L);

        assertEquals(1, queryResultMapper.insertResult(result));

        var rows = executionResultMapper.selectMyLatestResults(
                userId, 0L, 100);

        assertTrue(rows.stream().anyMatch(row ->
                execution.getExecutionId().equals(row.getExecutionId())
                        && row.getBatchId() == null));

        var detail = executionResultMapper.selectMyResultDetail(
                execution.getExecutionId(), userId);

        assertNotNull(detail);
        assertEquals(requestId, detail.getRequestId());
        assertEquals(result.getResultId(), detail.getResultId());
        assertNull(detail.getBatchId());

        var file = executionResultMapper.selectMyResultFile(
                execution.getExecutionId(), userId);

        assertNotNull(file);
        assertEquals(result.getResultId(), file.getResultId());
        assertEquals("standalone-mapper-test.csv", file.getFilePath());

        var context = resultAccessLogMapper.selectAccessContext(
                execution.getExecutionId(), userId);

        assertNotNull(context);
        assertEquals(requestId, context.getRequestId());
        assertEquals(result.getResultId(), context.getResultId());
        assertEquals(userId, context.getUserId());
    }

    @Test
    void 예전_배치_실행보다_새로운_개별_실행을_최신으로_조회한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        Long batchId = executionBatchService.createInitialBatch(
                requestId, createExecutor());

        // 배치 시각을 크게 만들어도 최신 SQL 실행 선정에 영향을 주면 안 된다.
        jdbcTemplate.update("""
                UPDATE EXECUTION_BATCH
                SET REQUESTED_AT = TIMESTAMP '2099-01-01 00:00:00'
                WHERE BATCH_ID = ?
                """, batchId);

        QueryExecution execution = new QueryExecution();
        execution.setQueryId(queryId);
        execution.setBatchId(null);
        execution.setExecutedBy(userId);
        execution.setAttemptNo(2);

        queryExecutionMapper.insertExecution(execution);

        var rows = executionResultMapper.selectMyLatestResults(
                userId, 0L, 100);

        var queryRows = rows.stream()
                .filter(row -> queryId.equals(row.getQueryId()))
                .toList();

        assertEquals(1, queryRows.size());
        assertEquals(
                execution.getExecutionId(),
                queryRows.get(0).getExecutionId());
        assertNull(queryRows.get(0).getBatchId());
    }
    
    @Test
    void SQL별로_기존_실행_이력을_확인한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        assertEquals(
                0,
                queryExecutionMapper.countExecutionsByQueryId(queryId));

        QueryExecution execution = createStandaloneExecution(queryId);

        assertEquals(
                1,
                queryExecutionMapper.countExecutionsByQueryId(queryId));

        // 실패한 이력도 이미 실행을 시도한 기록으로 취급한다.
        assertEquals(
                1,
                queryExecutionMapper.markRunning(execution.getExecutionId()));

        execution.setStatus("FAILED");
        execution.setDurationMs(10L);

        assertEquals(
                1,
                queryExecutionMapper.finishExecution(execution));

        assertEquals(
                1,
                queryExecutionMapper.countExecutionsByQueryId(queryId));
    }

    @Test
    void 같은_기안서의_대기와_실행중_이력만_집계한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        Long otherRequestId = createReadyRequest();

        assertEquals(
                0,
                queryExecutionMapper.countActiveExecutionsByRequestId(
                        requestId));

        QueryExecution execution = createStandaloneExecution(queryId);

        // PENDING도 이미 접수된 실행이다.
        assertEquals(
                1,
                queryExecutionMapper.countActiveExecutionsByRequestId(
                        requestId));

        // 다른 기안서에는 영향을 주지 않는다.
        assertEquals(
                0,
                queryExecutionMapper.countActiveExecutionsByRequestId(
                        otherRequestId));

        assertEquals(
                1,
                queryExecutionMapper.markRunning(execution.getExecutionId()));

        // RUNNING도 활성 실행으로 집계한다.
        assertEquals(
                1,
                queryExecutionMapper.countActiveExecutionsByRequestId(
                        requestId));

        execution.setStatus("SUCCESS");
        execution.setDurationMs(10L);

        assertEquals(
                1,
                queryExecutionMapper.finishExecution(execution));

        // 종료된 실행은 활성 실행에서 제외한다.
        assertEquals(
                0,
                queryExecutionMapper.countActiveExecutionsByRequestId(
                        requestId));
    }
    
    @Test
    void 접수_거절_예약은_소속과_상태가_맞을때만_제거한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);
        Long otherRequestId = createReadyRequest();

        QueryExecution execution = createStandaloneExecution(queryId);
        Long executionId = execution.getExecutionId();

        // PENDING 상태는 이 복구 메서드의 대상이 아니다.
        assertEquals(
                0,
                queryExecutionMapper.deleteRejectedStandaloneReservation(
                        requestId, queryId, executionId));

        assertEquals(
                1,
                queryExecutionMapper.markRunning(executionId));

        // 다른 기안서 ID로는 제거할 수 없다.
        assertEquals(
                0,
                queryExecutionMapper.deleteRejectedStandaloneReservation(
                        otherRequestId, queryId, executionId));

        assertEquals(
                1,
                queryExecutionMapper.countExecutionsByRequestId(requestId));

        assertEquals(
                1,
                queryExecutionMapper.deleteRejectedStandaloneReservation(
                        requestId, queryId, executionId));

        assertEquals(
                0,
                queryExecutionMapper.countExecutionsByRequestId(requestId));
    }

    @Test
    void 종료된_실행은_접수거절_복구로_제거하지_않는다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        QueryExecution execution = createStandaloneExecution(queryId);
        queryExecutionMapper.markRunning(execution.getExecutionId());

        execution.setStatus("FAILED");
        execution.setDurationMs(10L);
        queryExecutionMapper.finishExecution(execution);

        assertEquals(
                0,
                queryExecutionMapper.deleteRejectedStandaloneReservation(
                        requestId, queryId, execution.getExecutionId()));

        assertEquals(
                1,
                queryExecutionMapper.countExecutionsByRequestId(requestId));
    }
    
    @Test
    void 기안서_집계용_조회는_미실행_SQL과_최신_실행을_반환한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        var before = queryExecutionMapper
                .selectLatestExecutionsByRequestId(requestId);

        assertEquals(1, before.size());
        assertEquals(queryId, before.get(0).getQueryId());
        assertNull(before.get(0).getExecutionId());
        assertNull(before.get(0).getStatus());

        QueryExecution first = createStandaloneExecution(queryId);
        queryExecutionMapper.markRunning(first.getExecutionId());

        first.setStatus("FAILED");
        first.setDurationMs(10L);
        queryExecutionMapper.finishExecution(first);

        // 조회 검증용 재실행 이력. 실제 재실행 API는 별도로 구현한다.
        QueryExecution retry = new QueryExecution();
        retry.setQueryId(queryId);
        retry.setBatchId(null);
        retry.setExecutedBy(userId);
        retry.setAttemptNo(2);
        retry.setRetryOfExecutionId(first.getExecutionId());

        queryExecutionMapper.insertExecution(retry);

        var latest = queryExecutionMapper
                .selectLatestExecutionsByRequestId(requestId);

        assertEquals(1, latest.size());
        assertEquals(
                retry.getExecutionId(),
                latest.get(0).getExecutionId());
        assertEquals("PENDING", latest.get(0).getStatus());
    }

    /** 실제 SQL은 실행하지 않고 개별 실행 대기 이력만 만든다. */
    private QueryExecution createStandaloneExecution(Long queryId) {
        QueryExecution execution = new QueryExecution();
        execution.setQueryId(queryId);
        execution.setBatchId(null);
        execution.setExecutedBy(userId);
        execution.setAttemptNo(1);

        assertEquals(
                1,
                queryExecutionMapper.insertExecution(execution));

        return execution;
    }
    
    @Test
    void 실행_상세에_미실행_SQL도_표시한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        var items = executionQueueMapper.selectRequestSqlItems(requestId);

        assertEquals(1, items.size());

        var item = items.get(0);
        assertEquals(queryId, item.getQueryId());
        assertEquals(1, item.getQueryOrder());
        assertEquals(TEST_SQL, item.getSqlText());
        assertNull(item.getExecutionId());
        assertNull(item.getExecutionStatus());
    }

    @Test
    void 실행_상세는_배치_이력과_새로운_개별_이력을_중복없이_조회한다() {
        Long requestId = createReadyRequest();
        Long queryId = createQueryItem(requestId);

        Long batchId = executionBatchService.createInitialBatch(
                requestId, createExecutor());

        Long firstExecutionId = jdbcTemplate.queryForObject("""
                SELECT EXECUTION_ID
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                  AND QUERY_ID = ?
                """, Long.class, batchId, queryId);

        // 기존 배치 이력이 정상적으로 조회되는지 확인한다.
        var before = executionQueueMapper.selectRequestSqlItems(requestId);

        assertEquals(1, before.size());
        assertEquals(firstExecutionId, before.get(0).getExecutionId());
        assertEquals("PENDING", before.get(0).getExecutionStatus());

        queryExecutionMapper.markRunning(firstExecutionId);

        QueryExecution failed = new QueryExecution();
        failed.setExecutionId(firstExecutionId);
        failed.setStatus("FAILED");
        failed.setDurationMs(10L);
        failed.setErrorCode("TEST_ERROR");
        failed.setErrorMessage("테스트 실패");

        assertEquals(1, queryExecutionMapper.finishExecution(failed));

        // 최신 실행 선정 검증용 이력이며 실제 재실행 API는 호출하지 않는다.
        QueryExecution retry = new QueryExecution();
        retry.setQueryId(queryId);
        retry.setBatchId(null);
        retry.setExecutedBy(userId);
        retry.setAttemptNo(2);
        retry.setRetryOfExecutionId(firstExecutionId);

        assertEquals(1, queryExecutionMapper.insertExecution(retry));
        assertEquals(
                1,
                queryExecutionMapper.markRunning(retry.getExecutionId()));

        var after = executionQueueMapper.selectRequestSqlItems(requestId);

        // 같은 SQL이 과거 실행 횟수만큼 중복 표시되면 안 된다.
        assertEquals(1, after.size());

        var item = after.get(0);
        assertEquals(queryId, item.getQueryId());
        assertEquals(retry.getExecutionId(), item.getExecutionId());
        assertEquals("RUNNING", item.getExecutionStatus());
        assertNotNull(item.getStartedAt());
        assertNull(item.getErrorMessage());
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