package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.request.dto.DepartmentRequestListResponse;
import com.sqlflow.request.dto.RequestApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;

@Mapper
public interface DepartmentRequestMapper {

    long countVisibleRequests(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("type") String type,
            @Param("status") String status);

    List<DepartmentRequestListResponse> selectVisibleRequestsPage(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("type") String type,
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

    int insertChangeSqlResult(
            @Param("requestId") Long requestId,
            @Param("queryId") Long queryId,
            @Param("actorId") Long actorId,
            @Param("resultStatus") String resultStatus,
            @Param("resultNote") String resultNote,
            @Param("executedBy") Long executedBy);

    String lockChangeSqlResult(
            @Param("requestId") Long requestId,
            @Param("queryId") Long queryId,
            @Param("actorId") Long actorId);

    int insertChangeSqlResultHistory(
            @Param("queryId") Long queryId,
            @Param("actorId") Long actorId,
            @Param("changeType") String changeType,
            @Param("changeReason") String changeReason);

    int updateChangeSqlResult(
            @Param("queryId") Long queryId,
            @Param("actorId") Long actorId,
            @Param("resultStatus") String resultStatus,
            @Param("resultNote") String resultNote,
            @Param("executedBy") Long executedBy);

    int canManageChangeSqlResult(
            @Param("requestId") Long requestId,
            @Param("actorId") Long actorId);
}