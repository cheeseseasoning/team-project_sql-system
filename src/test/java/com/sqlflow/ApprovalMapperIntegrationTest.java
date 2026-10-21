package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
import com.sqlflow.approval.service.ApprovalService;
import com.sqlflow.request.dto.RequestDraftCreateRequest;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.service.RequestService;


@SpringBootTest
class ApprovalMapperIntegrationTest {

    @Autowired
    private ApprovalMapper approvalMapper;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RequestService requestService;

    @Test
    void candidateQueriesExecuteAgainstOracleSchema() {
        assertNotNull(approvalMapper.selectDepartmentApprovers(-1L));
        assertNotNull(approvalMapper.selectDbApprovers(-1L));
    }

    @Test
    void pendingApprovalQueriesExecuteAgainstOracleSchema() {
        assertEquals(List.of(), approvalMapper.selectPendingApprovals(Long.MIN_VALUE));
        assertNull(approvalMapper.selectPendingApprovalDetail(
                Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(List.of(), approvalMapper.selectPendingApprovalItems(
                Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(0, approvalMapper.canViewApprovalHistory(
        		Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(List.of(),
        		approvalMapper.selectApprovalHistory(Long.MIN_VALUE));
    }

    @Test
    @Transactional
    void approvalLockAndUpdateStatementsExecuteAgainstOracle() {
        assertNull(approvalMapper.selectApprovalRequestForUpdate(Long.MIN_VALUE));
        assertNull(approvalMapper.selectPendingApprovalForUpdate(
                Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(0, approvalMapper.approvePendingApproval(
                Long.MIN_VALUE, Long.MIN_VALUE));
        assertEquals(0, approvalMapper.activateNextApproval(Long.MIN_VALUE));
        assertEquals(0, approvalMapper.markRequestApproved(Long.MIN_VALUE));
    }

    @Test
    @Transactional
    void completeApprovalFlowRunsAgainstOracleAndRollsBackAfterTest() {
        SubmissionFixture fixture = findSubmissionFixture();
        assumeTrue(fixture != null,
                "활성 요청자·부서장·DB_APPROVER 조합이 없어 상태 전이 검증을 건너뜁니다.");

        RequestDraftCreateRequest draft = new RequestDraftCreateRequest();
        draft.setTitle("Oracle 결재 연동 테스트 " + System.nanoTime());
        draft.setPurpose("테스트 트랜잭션 롤백 확인");
        draft.setIsUrgent("N");
        draft.setSqls(List.of("SELECT 1 FROM DUAL"));
        draft.setSqlTitles(List.of("Oracle integration test"));

        Long requestId = requestService.createDraft(
                draft, fixture.requesterId(), fixture.deptId());
        RequestSubmitRequest submit = new RequestSubmitRequest();
        submit.setDbApproverId(fixture.dbApprover().getUserId());
        requestService.submitRequest(requestId, submit, fixture.requesterId());

        assertEquals("PENDING_APPROVAL", jdbcTemplate.queryForObject(
                "SELECT REQUEST_STATUS FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT STATUS FROM APPROVAL WHERE REQUEST_ID = ? AND APPROVAL_ORDER = 1",
                String.class,
                requestId));
        assertEquals("WAITING", jdbcTemplate.queryForObject(
                "SELECT STATUS FROM APPROVAL WHERE REQUEST_ID = ? AND APPROVAL_ORDER = 2",
                String.class,
                requestId));

        approvalService.approve(requestId, fixture.director().getUserId());

        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT STATUS FROM APPROVAL WHERE REQUEST_ID = ? AND APPROVAL_ORDER = 1",
                String.class,
                requestId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT STATUS FROM APPROVAL WHERE REQUEST_ID = ? AND APPROVAL_ORDER = 2",
                String.class,
                requestId));

        approvalService.approve(requestId, fixture.dbApprover().getUserId());

        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT APPROVAL_STATUS FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId));
        assertEquals("READY", jdbcTemplate.queryForObject(
                "SELECT REQUEST_STATUS FROM QUERY_REQUEST WHERE REQUEST_ID = ?",
                String.class,
                requestId));
        assertEquals("APPROVED", jdbcTemplate.queryForObject(
                "SELECT STATUS FROM APPROVAL WHERE REQUEST_ID = ? AND APPROVAL_ORDER = 2",
                String.class,
                requestId));
        
        List<ApprovalHistoryResponse> history =
                approvalService.getApprovalHistory(requestId, fixture.requesterId());

        assertEquals(2, history.size());

        assertEquals(1, history.get(0).getApprovalOrder());
        assertEquals("DIRECTOR", history.get(0).getApprovalType());
        assertEquals("APPROVED", history.get(0).getStatus());
        assertNotNull(history.get(0).getDecidedAt());

        assertEquals(2, history.get(1).getApprovalOrder());
        assertEquals("DB_EXECUTION", history.get(1).getApprovalType());
        assertEquals("APPROVED", history.get(1).getStatus());
        assertNotNull(history.get(1).getDecidedAt());
    }

    private SubmissionFixture findSubmissionFixture() {
        List<Submitter> submitters = jdbcTemplate.query(
                """
                SELECT U.USER_ID, U.DEPT_ID
                FROM USERS U
                WHERE U.ENABLED = 'Y'
                  AND EXISTS (
                      SELECT 1
                      FROM USERS H
                      JOIN POSITION P ON P.POSITION_ID = H.POSITION_ID
                      WHERE H.DEPT_ID = U.DEPT_ID
                        AND H.ENABLED = 'Y'
                        AND H.USER_ID <> U.USER_ID
                        AND P.POSITION_CODE = 'DIRECTOR'
                  )
                ORDER BY U.USER_ID
                """,
                (rs, rowNum) -> new Submitter(
                        rs.getLong("USER_ID"), rs.getLong("DEPT_ID")));

        for (Submitter submitter : submitters) {
            List<ApprovalCandidateResponse> directors =
                    approvalMapper.selectDepartmentApprovers(submitter.deptId());
            if (directors.size() != 1
                    || directors.get(0).getUserId().equals(submitter.userId())) {
                continue;
            }

            List<ApprovalCandidateResponse> dbApprovers = approvalMapper
                    .selectDbApprovers(directors.get(0).getUserId());
            ApprovalCandidateResponse dbApprover = dbApprovers.stream()
                    .filter(candidate -> !candidate.getUserId().equals(submitter.userId()))
                    .findFirst()
                    .orElse(null);
            if (dbApprover != null) {
                return new SubmissionFixture(
                        submitter.userId(), submitter.deptId(), directors.get(0), dbApprover);
            }
        }

        return null;
    }

    private record Submitter(Long userId, Long deptId) {
    }

    private record SubmissionFixture(
            Long requesterId,
            Long deptId,
            ApprovalCandidateResponse director,
            ApprovalCandidateResponse dbApprover) {
    }
}
