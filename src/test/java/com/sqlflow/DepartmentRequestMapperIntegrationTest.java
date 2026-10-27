package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.request.mapper.DepartmentRequestMapper;

@SpringBootTest
@Transactional(transactionManager = "managementTransactionManager")
class DepartmentRequestMapperIntegrationTest {

    @Autowired
    private DepartmentRequestMapper mapper;

    @Autowired
    private UserAccountMapper userMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private String prefix;
    private String passwordHash;

    private Long businessDeptId;
    private Long dbDeptId;
    private Long positionId;

    private Long businessDrafterId;
    private Long businessMemberId;
    private Long dbDrafterId;
    private Long dbMemberId;

    private Long businessSubmittedId;
    private Long dbSubmittedId;
    private Long businessDraftId;
    private Long dbDraftId;
    

    @BeforeEach
    void setUp() {
        prefix = "BOX-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        passwordHash = new BCryptPasswordEncoder().encode("test-only");

        businessDeptId = jdbc.queryForObject("""
                SELECT DEPT_ID
                FROM DEPARTMENT
                WHERE DEPT_NAME <> 'DB팀'
                ORDER BY DEPT_ID
                FETCH FIRST 1 ROW ONLY
                """, Long.class);

        dbDeptId = jdbc.queryForObject("""
                SELECT DEPT_ID
                FROM DEPARTMENT
                WHERE DEPT_NAME = 'DB팀'
                """, Long.class);

        positionId = jdbc.queryForObject("""
                SELECT POSITION_ID
                FROM POSITION
                ORDER BY POSITION_ID
                FETCH FIRST 1 ROW ONLY
                """, Long.class);

        businessDrafterId = createUser(businessDeptId, "작성자");
        businessMemberId = createUser(businessDeptId, "부서원");
        dbDrafterId = createUser(dbDeptId, "DB작성자");
        dbMemberId = createUser(dbDeptId, "DB담당자");

        businessSubmittedId = createRequest(
                businessDrafterId, businessDeptId, "업무부서 기안", true);

        businessDraftId = createRequest(
                businessDrafterId, businessDeptId, "업무부서 초안", false);

        dbSubmittedId = createRequest(
                dbDrafterId, dbDeptId, "DB부서 기안", true);

        dbDraftId = createRequest(
                dbDrafterId, dbDeptId, "DB부서 초안", false);
    }

    @Test
    void departmentMemberSeesColleaguesSubmittedRequestOnly() {
        assertVisible(
                businessMemberId,
                Set.of(businessSubmittedId));
    }

    @Test
    void authorDoesNotSeeOwnDraftInDepartmentBox() {
        assertVisible(
                businessDrafterId,
                Set.of(businessSubmittedId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_APPROVER", "DB_ADMIN"})
    void authorizedDbMemberSeesAllDepartments(String roleCode) {
        grantRole(dbMemberId, roleCode);

        assertVisible(
                dbMemberId,
                Set.of(businessSubmittedId, dbSubmittedId));
    }

    @Test
    void dbMemberWithoutRoleSeesOnlyOwnDepartment() {
        assertVisible(
                dbMemberId,
                Set.of(dbSubmittedId));
    }

    @Test
    void dbRoleOutsideDbDepartmentDoesNotGrantGlobalAccess() {
        grantRole(businessMemberId, "DB_ADMIN");

        assertVisible(
                businessMemberId,
                Set.of(businessSubmittedId));
    }

    @Test
    void disabledUserCannotReadRequests() {
        jdbc.update(
                "UPDATE USERS SET ENABLED = 'N' WHERE USER_ID = ?",
                businessMemberId);

        assertVisible(businessMemberId, Set.of());
    }

    @Test
    void currentDepartmentIsUsedAfterTransfer() {
        jdbc.update(
                "UPDATE USERS SET DEPT_ID = ? WHERE USER_ID = ?",
                dbDeptId,
                businessMemberId);

        assertVisible(
                businessMemberId,
                Set.of(dbSubmittedId));
    }

    @Test
    void searchStatusAndPaginationUseSameScope() {
        grantRole(dbMemberId, "DB_APPROVER");

        assertEquals(
                1L,
                mapper.countVisibleRequests(
                        dbMemberId,
                        prefix + "업무부서",
                        "READY"));

        assertEquals(
                0L,
                mapper.countVisibleRequests(
                        dbMemberId,
                        prefix,
                        "DRAFT"));

        var first = mapper.selectVisibleRequestsPage(
                dbMemberId, prefix, "", 0, 1);

        var second = mapper.selectVisibleRequestsPage(
                dbMemberId, prefix, "", 1, 1);

        assertEquals(1, first.size());
        assertEquals(1, second.size());

        assertNotEquals(
                first.get(0).getRequestId(),
                second.get(0).getRequestId());

        assertEquals(
                Set.of(businessSubmittedId, dbSubmittedId),
                Set.of(
                        first.get(0).getRequestId(),
                        second.get(0).getRequestId()));
    }
    
    @Test
    void memberCanReadDepartmentDetailButNotAnotherDepartment() {
        assertDetailVisible(businessSubmittedId, businessMemberId);
        assertDetailHidden(dbSubmittedId, businessMemberId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DB_APPROVER", "DB_ADMIN"})
    void authorizedDbMemberCanReadDetailsAcrossDepartments(String roleCode) {
        grantRole(dbMemberId, roleCode);

        assertDetailVisible(businessSubmittedId, dbMemberId);
        assertDetailVisible(dbSubmittedId, dbMemberId);

        assertDetailHidden(businessDraftId, dbMemberId);
        assertDetailHidden(dbDraftId, dbMemberId);
    }

    @Test
    void departmentDetailDoesNotExposeAuthorsOwnDraft() {
        assertDetailHidden(businessDraftId, businessDrafterId);
    }

    @Test
    void dbMemberWithoutRoleCannotReadAnotherDepartmentsDetail() {
        assertDetailHidden(businessSubmittedId, dbMemberId);
    }

    @Test
    void disabledUserCannotReadDetailItemsOrApprovals() {
        jdbc.update(
                "UPDATE USERS SET ENABLED = 'N' WHERE USER_ID = ?",
                businessMemberId);

        assertDetailHidden(businessSubmittedId, businessMemberId);
    }

    private void assertDetailVisible(Long requestId, Long actorId) {
        var detail = mapper.selectVisibleRequestDetail(requestId, actorId);

        assertNotNull(detail);
        assertEquals(requestId, detail.getRequestId());
        assertNotNull(detail.getDrafterName());

        var items = mapper.selectVisibleRequestItems(requestId, actorId);

        assertEquals(1, items.size());
        assertEquals("SELECT 1 FROM DUAL", items.get(0).getSqlText());

        var approvals = mapper.selectVisibleRequestApprovals(
                requestId, actorId);

        assertEquals(1, approvals.size());
        assertNotNull(approvals.get(0).getApproverName());
    }

    private void assertDetailHidden(Long requestId, Long actorId) {
        assertNull(mapper.selectVisibleRequestDetail(requestId, actorId));

        assertTrue(mapper.selectVisibleRequestItems(
                requestId, actorId).isEmpty());

        assertTrue(mapper.selectVisibleRequestApprovals(
                requestId, actorId).isEmpty());
    }

    private void assertVisible(Long actorId, Set<Long> expectedIds) {
        assertEquals(
                (long) expectedIds.size(),
                mapper.countVisibleRequests(actorId, prefix, ""));

        var items = mapper.selectVisibleRequestsPage(
                actorId, prefix, "", 0, 50);

        assertEquals(
                expectedIds,
                items.stream()
                        .map(item -> item.getRequestId())
                        .collect(Collectors.toSet()));

        items.forEach(item -> {
            assertNotNull(item.getDrafterName());
            assertNotNull(item.getDeptName());
            assertNotNull(item.getSubmittedAt());
            assertNotEquals("DRAFT", item.getRequestStatus());
        });
    }

    private Long createUser(Long deptId, String name) {
        Long userId = userMapper.nextUserId();

        assertEquals(
                1,
                userMapper.insertUser(
                        userId,
                        deptId,
                        positionId,
                        prefix + userId,
                        passwordHash,
                        prefix + name));

        return userId;
    }

    private Long createRequest(
            Long drafterId,
            Long deptId,
            String title,
            boolean submitted) {

        Long requestId = jdbc.queryForObject(
                "SELECT SEQ_QUERY_REQUEST.NEXTVAL FROM DUAL",
                Long.class);

        jdbc.update("""
                INSERT INTO QUERY_REQUEST (
                    REQUEST_ID,
                    REQUEST_NO,
                    DRAFTER_ID,
                    DEPT_ID,
                    TITLE,
                    PURPOSE,
                    REQUEST_STATUS,
                    SUBMITTED_AT
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?,
                    CASE WHEN ? = 1 THEN SYSTIMESTAMP ELSE NULL END
                )
                """,
                requestId,
                prefix + requestId,
                drafterId,
                deptId,
                prefix + title,
                "부서 기안함 조회 권한 테스트",
                submitted ? "READY" : "DRAFT",
                submitted ? 1 : 0);

        jdbc.update("""
                INSERT INTO QUERY_ITEM (
                    QUERY_ID,
                    REQUEST_ID,
                    QUERY_ORDER,
                    QUERY_TITLE,
                    SQL_TEXT,
                    AUTHORING_TYPE
                ) VALUES (
                    SEQ_QUERY_ITEM.NEXTVAL, ?, 1, ?, ?, 'MANUAL'
                )
                """,
                requestId,
                "상세 권한 테스트 SQL",
                "SELECT 1 FROM DUAL");

        jdbc.update("""
                INSERT INTO APPROVAL (
                    APPROVAL_ID,
                    REQUEST_ID,
                    APPROVER_ID,
                    APPROVAL_ORDER,
                    APPROVAL_TYPE,
                    STATUS,
                    CREATED_AT
                ) VALUES (
                    SEQ_APPROVAL.NEXTVAL, ?, ?, 1,
                    'DIRECTOR', 'PENDING', SYSTIMESTAMP
                )
                """,
                requestId,
                drafterId);

        return requestId;
    }

    private void grantRole(Long userId, String roleCode) {
        var roleIds = userMapper.selectRoleIdsByCodes(
                java.util.List.of(roleCode));

        assertEquals(
                1,
                roleIds.size(),
                roleCode + " 역할이 DB에 등록되어 있어야 합니다.");

        assertEquals(
                1,
                userMapper.insertUserRole(userId, roleIds.get(0)));
    }
}