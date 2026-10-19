package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;
import com.sqlflow.request.dto.RequestListResponse;

@Mapper
public interface RequestMapper {

    Long selectNextRequestId();

    int insertRequest(QueryRequest request);

    int insertItem(QueryItem item);
    
    List<RequestListResponse> selectMyRequests(@Param("drafterId") Long drafterId);
}