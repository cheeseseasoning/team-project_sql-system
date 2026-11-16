package com.sqlflow.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.approval.domain.ApprovalActionContext;
import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.domain.PendingApproval;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.ApprovalDepartmentCandidateResponse;
import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;

@Mapper
public interface ApprovalMapper {

    List<PendingApprovalResponse> selectPendingApprovals(
            @Param("approverId") Long approverId);

    int canViewApprovalHistory(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    List<ApprovalHistoryResponse> selectApprovalHistory(
            @Param("requestId") Long requestId);

    RequestDetailResponse selectPendingApprovalDetail(
            @Param("requestId") Long requestId,
            @Param("approverId") Long approverId);

    List<RequestItemResponse> selectPendingApprovalItems(
            @Param("requestId") Long requestId,
            @Param("approverId") Long approverId);

    ApprovalRequestContext selectRequestContext(
            @Param("requestId") Long requestId);

    List<ApprovalCandidateResponse> selectDepartmentApprovers(
            @Param("deptId") Long deptId);

    List<ApprovalCandidateResponse> selectDbApprovers(
            @Param("departmentApproverId") Long departmentApproverId);

    List<ApprovalCandidateResponse> searchActiveCandidates(
            @Param("keyword") String keyword,
            @Param("excludedUserId") Long excludedUserId);
    
    /** 부서 결재 후보와 기안자를 제외한 현재 결재 가능 인원을 조회한다. */
    List<ApprovalDepartmentCandidateResponse> searchDepartmentCandidates(
            @Param("keyword") String keyword,
            @Param("excludedUserId") Long excludedUserId);

    int insertApproval(
            @Param("requestId") Long requestId,
            @Param("approverId") Long approverId,
            @Param("approvalOrder") int approvalOrder,
            @Param("approvalType") String approvalType,
            @Param("status") String status);
    
    /** 개인 또는 부서를 담당 대상으로 지정한 결재 단계 한 건을 저장한다. */
    int insertApprovalTarget(
            @Param("requestId") Long requestId,
            @Param("targetType") String targetType,
            @Param("approverId") Long approverId,
            @Param("approverDeptId") Long approverDeptId,
            @Param("approvalOrder") int approvalOrder,
            @Param("approvalType") String approvalType,
            @Param("status") String status);

    ApprovalActionContext selectApprovalRequestForUpdate(
            @Param("requestId") Long requestId);

    PendingApproval selectPendingApprovalForUpdate(
            @Param("requestId") Long requestId,
            @Param("approverId") Long approverId);

    int approvePendingApproval(
            @Param("approvalId") Long approvalId,
            @Param("approverId") Long approverId);

    int activateNextApproval(
            @Param("requestId") Long requestId);

    int markRequestApproved(
            @Param("requestId") Long requestId);

    int rejectPendingApproval(
            @Param("approvalId") Long approvalId,
            @Param("approverId") Long approverId,
            @Param("commentText") String commentText);

    int cancelWaitingApprovals(
            @Param("requestId") Long requestId);

    int markRequestRejected(
            @Param("requestId") Long requestId);
}