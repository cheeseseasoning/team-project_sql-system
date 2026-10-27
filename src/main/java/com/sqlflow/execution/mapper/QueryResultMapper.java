package com.sqlflow.execution.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.ExpiredResultFile;
import com.sqlflow.execution.domain.QueryResult;

@Mapper
public interface QueryResultMapper {

    int insertResult(QueryResult result);

    LocalDateTime selectCleanupCutoff();

    List<ExpiredResultFile> selectExpiredResults(
            @Param("afterResultId") long afterResultId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("limit") int limit);

    int markExpiredResultDeleted(
            @Param("resultId") Long resultId,
            @Param("filePath") String filePath,
            @Param("cutoff") LocalDateTime cutoff);
    
    ExpiredResultFile selectExpiredResultForUpdate(
            @Param("resultId") Long resultId,
            @Param("filePath") String filePath,
            @Param("cutoff") LocalDateTime cutoff);
}