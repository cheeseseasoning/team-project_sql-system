package com.sqlflow.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.domain.ApprovalActionContext;
import com.sqlflow.approval.domain.PendingApproval;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;

@Mapper
public interface ApprovalMapper {

    List<PendingApprovalResponse> selectPendingApprovals(
            @Param("approverId") Long approverId);

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

    int insertApproval(
            @Param("requestId") Long requestId,
            @Param("approverId") Long approverId,
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

    int activateNextApproval(@Param("requestId") Long requestId);

    int markRequestApproved(@Param("requestId") Long requestId);
    
    int rejectPendingApproval(
    		@Param("approvalId") Long approvalId,
    		@Param("approverId") Long approverId,
    		@Param("commentText") String commentText);
    
    int cancelWaitingApprovals(
    		@Param("requestId") Long requestId);
    
    int markRequestRejected(
    		@Param("requestId") Long requestId);
}










