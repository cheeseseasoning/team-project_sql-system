package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestListResponse;
import com.sqlflow.request.dto.RequestSummaryResponse;
import com.sqlflow.request.dto.ViewCreateResultResponse;
import com.sqlflow.request.dto.ViewCreateResultHistoryResponse;

@Mapper
public interface RequestMapper {

    Long selectNextRequestId();

    int insertRequest(QueryRequest request);

    int insertItem(QueryItem item);

    int insertViewSql(@Param("requestId") Long requestId,
                      @Param("queryOrder") int queryOrder,
                      @Param("sqlTitle") String sqlTitle,
                      @Param("sqlContent") String sqlContent);

    List<RequestItemResponse> selectMyViewSqlItems(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);

    int deleteDraftViewSql(@Param("requestId") Long requestId,
                           @Param("drafterId") Long drafterId);

    int updateRequestType(@Param("requestId") Long requestId,
                          @Param("drafterId") Long drafterId,
                          @Param("requestType") String requestType);

    List<RequestItemResponse> selectApprovedViewSqlItems(@Param("requestId") Long requestId);

    int canDownloadApprovedViewSql(@Param("requestId") Long requestId,
                                   @Param("actorId") Long actorId);

    ViewCreateResultResponse selectViewCreateResult(@Param("requestId") Long requestId);

    List<ViewCreateResultHistoryResponse> selectViewCreateResultHistory(@Param("requestId") Long requestId);
    
    List<RequestListResponse> selectMyRequests(
            @Param("drafterId") Long drafterId,
            @Param("keyword") String keyword,
            @Param("status") String status);
    
    RequestDetailResponse selectMyRequestDetail(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);

    List<RequestItemResponse> selectMyRequestItems(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);
    
    // 본인 요청의 상태 확인 및 수정 중 잠금
    String selectMyRequestStatusForUpdate(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);

    // 초안의 제목·목적 수정
    int updateDraft(QueryRequest request);

    // 초안의 기존 SQL 항목 삭제
    int deleteDraftItems(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);
    
    // 본인의 초안 요청서 삭제
    int deleteDraftRequest(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);
    
    // 본인의 초안을 제출 상태로 변경
    int submitDraft(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);
    
    long countMyRequests(
            @Param("drafterId") Long drafterId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    List<RequestListResponse> selectMyRequestsPage(
            @Param("drafterId") Long drafterId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("size") int size);
    
    RequestSummaryResponse selectMyRequestSummary(
            @Param("drafterId") Long drafterId);
    
    // 본인 요청에 저장된 결재선을 순서대로 조회
    List<RequestApprovalResponse> selectMyRequestApprovals(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);

    /** Save the validation status for one SQL belonging to the owner's draft. */
    int updateDraftItemValidationStatus(
            @Param("requestId") Long requestId,
            @Param("queryId") Long queryId,
            @Param("drafterId") Long drafterId,
            @Param("validationStatus") String validationStatus);
    
    int updateSubmittedItemValidation(
            @Param("requestId") Long requestId,
            @Param("queryId") Long queryId,
            @Param("itemClass") String itemClass);

    int deleteQueryResourceUsage(@Param("queryId") Long queryId);

    int insertQueryResourceUsage(
            @Param("queryId") Long queryId,
            @Param("resourceId") Long resourceId);

    int updateRequestClass(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId,
            @Param("requestClass") String requestClass);
    
    int withdrawRequest(
            @Param("requestId") Long requestId,
            @Param("drafterId") Long drafterId);

    int cancelApprovalsForWithdrawal(
            @Param("requestId") Long requestId);
}
