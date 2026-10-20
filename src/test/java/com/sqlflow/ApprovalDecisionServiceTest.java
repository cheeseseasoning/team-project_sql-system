package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.domain.ApprovalActionContext;
import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.domain.PendingApproval;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
import com.sqlflow.approval.service.ApprovalService;

class ApprovalDecisionServiceTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long APPROVER_ID = 20L;

    private final FakeApprovalMapper mapper = new FakeApprovalMapper();
    private ApprovalService approvalService;

    @BeforeEach
    void setUp() {
        approvalService = new ApprovalService(mapper);
        mapper.request.setRequestId(REQUEST_ID);
        mapper.request.setDrafterId(1L);
        mapper.request.setRequestStatus("PENDING_APPROVAL");
        mapper.request.setApprovalStatus("PENDING");
        mapper.currentPending.setApprovalId(200L);
        mapper.currentPending.setApprovalOrder(1);
        mapper.currentPending.setApprovalType("DIRECTOR");
    }

    @Test
    void departmentHeadApprovalActivatesDbApproval() {
        approvalService.approve(REQUEST_ID, APPROVER_ID);

        assertEquals("APPROVED", mapper.approvalStatus);
        assertEquals("PENDING", mapper.nextApprovalStatus);
        assertEquals("PENDING_APPROVAL", mapper.request.getRequestStatus());
        assertEquals("PENDING", mapper.request.getApprovalStatus());
    }

    @Test
    void dbFinalApprovalMarksApprovalAndRequestApprovedAndReady() {
        mapper.currentPending.setApprovalOrder(2);
        mapper.currentPending.setApprovalType("INTERMEDIATE");

        approvalService.approve(REQUEST_ID, APPROVER_ID);

        assertEquals("APPROVED", mapper.approvalStatus);
        assertEquals("APPROVED", mapper.request.getApprovalStatus());
        assertEquals("READY", mapper.request.getRequestStatus());
    }

    @Test
    void rejectsApprovalWhenRequestIsNotPending() {
        mapper.request.setRequestStatus("READY");
        mapper.request.setApprovalStatus("APPROVED");

        assertThrows(ResponseStatusException.class,
                () -> approvalService.approve(REQUEST_ID, APPROVER_ID));
        assertEquals("PENDING", mapper.approvalStatus);
    }

    @Test
    void requesterCannotApproveOwnRequest() {
        mapper.request.setDrafterId(APPROVER_ID);

        assertThrows(ResponseStatusException.class,
                () -> approvalService.approve(REQUEST_ID, APPROVER_ID));
        assertEquals("PENDING", mapper.approvalStatus);
    }

    @Test
    void userWithoutEligibleCurrentApprovalCannotApprove() {
        mapper.currentPending = null;

        assertThrows(ResponseStatusException.class,
                () -> approvalService.approve(REQUEST_ID, APPROVER_ID));
        assertEquals("PENDING", mapper.approvalStatus);
    }

    private static class FakeApprovalMapper implements ApprovalMapper {

        private final ApprovalActionContext request = new ApprovalActionContext();
        private PendingApproval currentPending = new PendingApproval();
        private String approvalStatus = "PENDING";
        private String nextApprovalStatus = "WAITING";

        @Override
        public List<PendingApprovalResponse> selectPendingApprovals(Long approverId) {
            return List.of();
        }

        @Override
        public com.sqlflow.request.dto.RequestDetailResponse selectPendingApprovalDetail(
                Long requestId, Long approverId) {
            return null;
        }

        @Override
        public List<com.sqlflow.request.dto.RequestItemResponse> selectPendingApprovalItems(
                Long requestId, Long approverId) {
            return List.of();
        }

        @Override
        public ApprovalRequestContext selectRequestContext(Long requestId) {
            return null;
        }

        @Override
        public List<ApprovalCandidateResponse> selectDepartmentApprovers(Long deptId) {
            return List.of();
        }

        @Override
        public List<ApprovalCandidateResponse> selectDbApprovers(Long departmentApproverId) {
            return List.of();
        }

        @Override
        public int insertApproval(
                Long requestId, Long approverId, int approvalOrder,
                String approvalType, String status) {
            return 1;
        }

        @Override
        public ApprovalActionContext selectApprovalRequestForUpdate(Long requestId) {
            return request;
        }

        @Override
        public PendingApproval selectPendingApprovalForUpdate(Long requestId, Long approverId) {
            return currentPending;
        }

        @Override
        public int approvePendingApproval(Long approvalId, Long approverId) {
            approvalStatus = "APPROVED";
            return 1;
        }

        @Override
        public int activateNextApproval(Long requestId) {
            nextApprovalStatus = "PENDING";
            return 1;
        }

        @Override
        public int markRequestApproved(Long requestId) {
            request.setApprovalStatus("APPROVED");
            request.setRequestStatus("READY");
            return 1;
        }
    }
}
