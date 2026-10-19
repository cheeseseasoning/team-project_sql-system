package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestListResponse;

@Mapper
public interface RequestMapper {

    Long selectNextRequestId();

    int insertRequest(QueryRequest request);

    int insertItem(QueryItem item);
    
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
    
}