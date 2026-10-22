package com.sqlflow.execution.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.sqlflow.execution.domain.QueryResult;

/** 결과 파일 정보를 Oracle 관리 DB에 저장한다. */
@Mapper
public interface QueryResultMapper {

    int insertResult(QueryResult result);
}