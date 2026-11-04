package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

    @Mock
    private ApprovalMapper approvalMapper;

    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(approvalMapper);
    }

    @Test
    void pendingApprovalListIsScopedToAuthenticatedApprover() {
        List<PendingApprovalResponse> expected = List.of(
                new PendingApprovalResponse(
                        REQUEST_ID,
                        "Q-20261019-100",
                        "SELECT",
                        "매출 조회",
                        "월간 보고",
                        "요청자",
                        "영업팀",
                        null,
                        1,
                        "APPROVER"));

        when(approvalMapper.selectPendingApprovals(ACTOR_ID))
                .thenReturn(expected);

        assertEquals(
                expected,
                approvalService.getPendingApprovals(ACTOR_ID));
    }

    @Test
    void pendingDetailIsNotVisibleToUnassignedUser() {
        when(approvalMapper.selectPendingApprovalDetail(
                REQUEST_ID, ACTOR_ID)).thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.getPendingApprovalDetail(
                        REQUEST_ID, ACTOR_ID));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void createsApprovalLineInRequestedOrderWithoutFixedApprovers() {
        stubOwnedDraft();
        stubActiveCandidates();

        when(approvalMapper.insertApproval(
                REQUEST_ID, 30L, 1, "APPROVER", "PENDING"))
                .thenReturn(1);
        when(approvalMapper.insertApproval(
                REQUEST_ID, 10L, 2, "APPROVER", "WAITING"))
                .thenReturn(1);

        approvalService.createApprovalLine(
                REQUEST_ID, ACTOR_ID, List.of(30L, 10L));

        InOrder order = inOrder(approvalMapper);

        order.verify(approvalMapper).insertApproval(
                REQUEST_ID, 30L, 1, "APPROVER", "PENDING");
        order.verify(approvalMapper).insertApproval(
                REQUEST_ID, 10L, 2, "APPROVER", "WAITING");

        verify(approvalMapper, never())
                .selectDepartmentApprovers(anyLong());
        verify(approvalMapper, never())
                .selectDbApprovers(anyLong());
    }

    @Test
    void allowsSingleOrdinaryApprover() {
        stubOwnedDraft();
        stubActiveCandidates();

        when(approvalMapper.insertApproval(
                REQUEST_ID, 10L, 1, "APPROVER", "PENDING"))
                .thenReturn(1);

        approvalService.createApprovalLine(
                REQUEST_ID, ACTOR_ID, List.of(10L));

        verify(approvalMapper).insertApproval(
                REQUEST_ID, 10L, 1, "APPROVER", "PENDING");
    }

    @Test
    void rejectsEmptyApprovalLine() {
        stubOwnedDraft();

        assertBadRequest(List.of());
    }

    @Test
    void rejectsNullApprovalLine() {
        stubOwnedDraft();

        assertBadRequest(null);
    }

    @Test
    void rejectsDuplicateApprovers() {
        stubOwnedDraft();

        assertBadRequest(List.of(10L, 10L));
    }

    @Test
    void rejectsRequesterAsApprover() {
        stubOwnedDraft();

        assertBadRequest(List.of(ACTOR_ID));
    }

    @Test
    void rejectsNullApprover() {
        stubOwnedDraft();

        assertBadRequest(Arrays.asList(10L, null));
    }

    @Test
    void rejectsNonPositiveApprover() {
        stubOwnedDraft();

        assertBadRequest(List.of(0L));
    }

    @Test
    void rejectsUnavailableApproverBeforeSavingAnyApproval() {
        stubOwnedDraft();
        stubActiveCandidates();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, ACTOR_ID, List.of(10L, 999L)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoApprovalInserted();
    }

    @Test
    void failsWhenApprovalInsertDoesNotSucceed() {
        stubOwnedDraft();
        stubActiveCandidates();

        when(approvalMapper.insertApproval(
                REQUEST_ID, 10L, 1, "APPROVER", "PENDING"))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, ACTOR_ID, List.of(10L)));
    }

    @Test
    void rejectsRequestNotOwnedByActor() {
        stubOwnedDraft();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, 999L, List.of(10L)));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoApprovalInserted();
    }
    
    @Test
    void candidateLookupReturnsActiveUsersWithoutFixedApprovers() {
        stubOwnedDraft();
        stubActiveCandidates();

        var result = approvalService.getApprovalCandidates(
                REQUEST_ID, ACTOR_ID, "");

        assertEquals(
                List.of(10L, 30L),
                result.getCandidates().stream()
                        .map(ApprovalCandidateResponse::getUserId)
                        .toList());

        verify(approvalMapper, never())
                .selectDepartmentApprovers(anyLong());
        verify(approvalMapper, never())
                .selectDbApprovers(anyLong());
    }

    @Test
    void candidateLookupTrimsSearchKeyword() {
        stubOwnedDraft();

        var expected = List.of(
                new ApprovalCandidateResponse(
                        10L, "김대리", "개발팀", "대리"));

        when(approvalMapper.searchActiveCandidates("김", ACTOR_ID))
                .thenReturn(expected);

        var result = approvalService.getApprovalCandidates(
                REQUEST_ID, ACTOR_ID, "  김  ");

        assertEquals(expected, result.getCandidates());
        verify(approvalMapper).searchActiveCandidates("김", ACTOR_ID);
    }

    @Test
    void candidateLookupRejectsRequestNotOwnedByActor() {
        stubOwnedDraft();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.getApprovalCandidates(
                        REQUEST_ID, 999L, ""));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(approvalMapper, never())
                .searchActiveCandidates(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void candidateLookupRejectsSubmittedRequest() {
        ApprovalRequestContext request = new ApprovalRequestContext();
        request.setRequestId(REQUEST_ID);
        request.setDrafterId(ACTOR_ID);
        request.setRequestStatus("PENDING_APPROVAL");

        when(approvalMapper.selectRequestContext(REQUEST_ID))
                .thenReturn(request);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.getApprovalCandidates(
                        REQUEST_ID, ACTOR_ID, ""));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(approvalMapper, never())
                .searchActiveCandidates(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    private void stubOwnedDraft() {
        ApprovalRequestContext request = new ApprovalRequestContext();
        request.setRequestId(REQUEST_ID);
        request.setDrafterId(ACTOR_ID);
        request.setDeptId(DEPARTMENT_ID);
        request.setRequestStatus("DRAFT");

        when(approvalMapper.selectRequestContext(REQUEST_ID))
                .thenReturn(request);
    }

    private void stubActiveCandidates() {
        when(approvalMapper.searchActiveCandidates(null, ACTOR_ID))
                .thenReturn(List.of(
                        new ApprovalCandidateResponse(
                                10L, "김대리", "개발팀", "대리"),
                        new ApprovalCandidateResponse(
                                30L, "이과장", "영업팀", "과장")));
    }

    private void assertBadRequest(List<Long> approverIds) {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> approvalService.createApprovalLine(
                        REQUEST_ID, ACTOR_ID, approverIds));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoApprovalInserted();
    }

    private void verifyNoApprovalInserted() {
        verify(approvalMapper, never()).insertApproval(
                anyLong(),
                anyLong(),
                anyInt(),
                anyString(),
                anyString());
    }
}