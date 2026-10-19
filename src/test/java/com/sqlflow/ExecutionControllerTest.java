package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;

/**
 * 실행 대기 등록 API의 인증·권한·CSRF와 정상 응답을 검증한다.
 * 테스트 데이터는 종료 후 롤백되지만 시퀀스 번호는 복구되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ExecutionControllerTest {

    private static final String API_URL = "/api/executions/requests/{requestId}/batches";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long deptId;
    private Long requestId;

    @BeforeEach
    void setUp() {
        // 외래키 연결에 필요한 기존 사용자 정보만 조회한다.
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

        requestId = createReadyRequest();
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        // CSRF는 통과시키고, 로그인하지 않은 상황만 검사한다.
        mockMvc.perform(post(API_URL, requestId)
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));

        assertNoBatchCreated();
    }

    @Test
    void userWithoutExecutionAuthorityIsRejected() throws Exception {
        SqlFlowUserDetails loginUser = createUser(List.of());

        mockMvc.perform(post(API_URL, requestId)
                        .with(user(loginUser))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("SQL 실행 권한이 없습니다."));

        assertNoBatchCreated();
    }

    @Test
    void authorizedRequestCreatesPendingBatch() throws Exception {
        SqlFlowUserDetails executor = createUser(List.of("DB_APPROVER"));

        String response = mockMvc.perform(post(API_URL, requestId)
                        .with(user(executor))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").isNumber())
                .andExpect(jsonPath("$.batchStatus").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        Number id = JsonPath.read(response, "$.batchId");
        Long batchId = id.longValue();

        // 응답만 성공한 것이 아니라 실제 DB에도 올바르게 저장됐는지 확인한다.
        Map<String, Object> batch = jdbcTemplate.queryForMap("""
                SELECT REQUEST_ID, INITIATED_BY, BATCH_STATUS
                FROM EXECUTION_BATCH
                WHERE BATCH_ID = ?
                """, batchId);

        assertEquals(requestId, ((Number) batch.get("REQUEST_ID")).longValue());
        assertEquals(userId, ((Number) batch.get("INITIATED_BY")).longValue());
        assertEquals("PENDING", batch.get("BATCH_STATUS"));

        Integer executionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM QUERY_EXECUTION
                WHERE BATCH_ID = ?
                  AND STATUS = 'PENDING'
                  AND ATTEMPT_NO = 1
                """, Integer.class, batchId);

        assertEquals(Integer.valueOf(1), executionCount);
    }

    @Test
    void requestWithoutCsrfTokenIsRejected() throws Exception {
        SqlFlowUserDetails executor = createUser(List.of("DB_APPROVER"));

        // 로그인과 실행 권한이 있어도 CSRF 토큰이 없으면 차단해야 한다.
        mockMvc.perform(post(API_URL, requestId)
                        .with(user(executor)))
                .andExpect(status().isForbidden());

        assertNoBatchCreated();
    }

    /**
     * 비밀번호 로그인 과정 대신 인증된 사용자 정보를 요청에 주입한다.
     * 실제 DB의 사용자 역할은 변경하지 않는다.
     */
    private SqlFlowUserDetails createUser(List<String> roleCodes) {
        UserAccount account = new UserAccount();
        account.setUserId(userId);
        account.setDeptId(deptId);
        account.setLoginId("execution-api-test-user");
        account.setUserName("실행 API 테스트 사용자");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(account, roleCodes);
    }

    /** 기존 요청을 변경하지 않고, 실행 가능한 테스트 요청과 SQL을 생성한다. */
    private Long createReadyRequest() {
        Long id = jdbcTemplate.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL", Long.class);

        jdbcTemplate.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID, REQUEST_NO, DRAFTER_ID, DEPT_ID,
                    TITLE, PURPOSE, REQUEST_STATUS, APPROVAL_STATUS
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "TEST-" + id,
                userId,
                deptId,
                "실행 API 테스트",
                "테스트 종료 후 롤백할 데이터",
                "READY",
                "APPROVED");

        jdbcTemplate.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID, REQUEST_ID, QUERY_ORDER,
                    QUERY_TITLE, SQL_TEXT, AUTHORING_TYPE
                ) VALUES (
                    SEQ_QUERY_ITEM.NEXTVAL, ?, ?, ?, ?, ?
                )
                """,
                id,
                1,
                "테스트 SQL",
                "SELECT 1 FROM DUAL",
                "MANUAL");

        return id;
    }

    /** 차단된 요청이 실행 묶음을 생성하지 않았는지 확인한다. */
    private void assertNoBatchCreated() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM EXECUTION_BATCH
                WHERE REQUEST_ID = ?
                """, Integer.class, requestId);

        assertEquals(Integer.valueOf(0), count);
    }
}