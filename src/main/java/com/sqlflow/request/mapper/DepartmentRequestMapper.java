package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.request.dto.DepartmentRequestListResponse;
import com.sqlflow.request.dto.RequestApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.ViewCreateResultResponse;
import com.sqlflow.request.dto.ViewCreateResultHistoryResponse;

@Mapper
public interface DepartmentRequestMapper {

    long countVisibleRequests(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    List<DepartmentRequestListResponse> selectVisibleRequestsPage(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("size") int size);
    
    RequestDetailResponse selectVisibleRequestDetail(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    List<RequestItemResponse> selectVisibleRequestItems(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    List<RequestItemResponse> selectVisibleViewSqlItems(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    List<RequestApprovalResponse> selectVisibleRequestApprovals(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);
    
    int countVisibleRequest(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    List<ApprovalHistoryResponse> selectVisibleRequestApprovalHistory(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    ViewCreateResultResponse selectVisibleViewCreateResult(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    int canRegisterViewCreateResult(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    Long lockEligibleViewCreateRequest(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);

    int insertViewCreateResultHistory(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId,
            @Param("changeReason") String changeReason);

    int insertViewCreateResult(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId,
            @Param("resultStatus") String resultStatus,
            @Param("resultContent") String resultContent);

    int updateViewCreateResult(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId,
            @Param("resultStatus") String resultStatus,
            @Param("resultContent") String resultContent);

    List<ViewCreateResultHistoryResponse> selectVisibleViewCreateResultHistory(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);
}
