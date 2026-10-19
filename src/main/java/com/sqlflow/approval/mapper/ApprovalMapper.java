package com.sqlflow.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;

@Mapper
public interface ApprovalMapper {

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
}
