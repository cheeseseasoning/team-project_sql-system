package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
import com.sqlflow.approval.service.ApprovalService;

@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long ACTOR_ID = 1L;
    private static final Long DEPARTMENT_ID = 5L;
    private static final Long DEPARTMENT_APPROVER_ID = 10L;
    private static final Long DB_APPROVER_ID = 20L;

    @Mock
    private ApprovalMapper approvalMapper;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(approvalMapper);
    }

    private void stubOwnedDraft() {
        ApprovalRequestContext request = new ApprovalRequestContext();
        request.setRequestId(REQUEST_ID);
        request.setDrafterId(ACTOR_ID);
        request.setDeptId(DEPARTMENT_ID);
        request.setRequestStatus("DRAFT");

        when(approvalMapper.selectRequestContext(REQUEST_ID)).thenReturn(request);
        when(approvalMapper.selectDepartmentApprovers(DEPARTMENT_ID))
                .thenReturn(List.of(candidate(
                        DEPARTMENT_APPROVER_ID, "부서장", "영업팀")));
    }

    @Test
    void candidateLookupReturnsDepartmentHeadAndFiltersRequester() {
        stubOwnedDraft();
        stubDbApprovers();

        var result = approvalService.getApprovalCandidates(REQUEST_ID, ACTOR_ID);

        assertEquals(DEPARTMENT_APPROVER_ID, result.departmentApprover().userId());
        assertEquals(List.of(DB_APPROVER_ID),
                result.dbApprovers().stream()
                        .map(ApprovalCandidateResponse::userId)
                        .toList());
        verify(approvalMapper).selectDepartmentApprovers(DEPARTMENT_ID);
    }

    @Test
    void pendingApprovalListIsScopedToAuthenticatedApprover() {
        List<PendingApprovalResponse> expected = List.of(
                new PendingApprovalResponse(
                        REQUEST_ID, "Q-20261019-100", "매출 조회", "월간 보고",
                        "요청자", "영업팀", null, 1, "DIRECTOR"));
        when(approvalMapper.selectPendingApprovals(ACTOR_ID)).thenReturn(expected);

        assertEquals(expected, approvalService.getPendingApprovals(ACTOR_ID));
        verify(approvalMapper).selectPendingApprovals(ACTOR_ID);
    }

    @Test
    void pendingDetailIsNotVisibleToUnassignedUser() {
        when(approvalMapper.selectPendingApprovalDetail(REQUEST_ID, ACTOR_ID))
                .thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.getPendingApprovalDetail(REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void submissionRevalidatesAndCreatesOrderedPendingApprovalLine() {
        stubOwnedDraft();
        stubDbApprovers();

        approvalService.createApprovalLine(
                REQUEST_ID, ACTOR_ID, DB_APPROVER_ID);

        verify(approvalMapper).insertApproval(
                REQUEST_ID, DEPARTMENT_APPROVER_ID, 1, "DIRECTOR", "PENDING");
        verify(approvalMapper).insertApproval(
                REQUEST_ID, DB_APPROVER_ID, 2, "INTERMEDIATE", "WAITING");
    }

    @Test
    void submissionRejectsDbApproverWhoIsNoLongerEligible() {
        stubOwnedDraft();
        stubDbApprovers();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, ACTOR_ID, 30L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(approvalMapper, never()).insertApproval(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void submissionRemainsBlockedWhenDepartmentHeadIsMissing() {
        stubOwnedDraft();
        when(approvalMapper.selectDepartmentApprovers(DEPARTMENT_ID))
                .thenReturn(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, ACTOR_ID, DB_APPROVER_ID));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(approvalMapper, never()).insertApproval(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    private ApprovalCandidateResponse candidate(
            Long userId, String userName, String deptName) {
        return new ApprovalCandidateResponse(userId, userName, deptName);
    }

    private void stubDbApprovers() {
        when(approvalMapper.selectDbApprovers(DEPARTMENT_APPROVER_ID))
                .thenReturn(List.of(
                        candidate(ACTOR_ID, "요청자", "DB팀"),
                        candidate(DB_APPROVER_ID, "DB 결재자", "DB팀")));
    }
}
