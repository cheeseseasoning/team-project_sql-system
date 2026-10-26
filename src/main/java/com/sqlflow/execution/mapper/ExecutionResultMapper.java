package com.sqlflow.execution.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.dto.ExecutionResultDetailResponse;
import com.sqlflow.execution.dto.ExecutionResultListResponse;

@Mapper
public interface ExecutionResultMapper {

    long countMyLatestResults(
            @Param("drafterId") Long drafterId);

    List<ExecutionResultListResponse> selectMyLatestResults(
            @Param("drafterId") Long drafterId,
            @Param("offset") long offset,
            @Param("size") int size);
    
    ExecutionResultDetailResponse selectMyResultDetail(
            @Param("executionId") Long executionId,
            @Param("drafterId") Long drafterId);
    
    ExecutionResultFile selectMyResultFile(
            @Param("executionId") Long executionId,
            @Param("drafterId") Long drafterId);
    
    List<QueryObjectName> selectResultTargets(
            @Param("queryId") Long queryId);
}