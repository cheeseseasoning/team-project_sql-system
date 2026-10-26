package com.sqlflow.execution.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.ResultAccessLog;
import com.sqlflow.execution.domain.ResultAccessLogContext;

@Mapper
public interface ResultAccessLogMapper {

    int insertAccessLog(ResultAccessLog log);

    ResultAccessLogContext selectAccessContext(
            @Param("executionId") Long executionId,
            @Param("userId") Long userId);
}