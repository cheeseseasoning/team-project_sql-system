package com.sqlflow.execution.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.domain.QueryExecutionTarget;

/**
 * SQL별 실행 이력의 등록과 상태 변경을 담당한다.
 */
@Mapper
public interface QueryExecutionMapper {

    /** 실행 대기 이력을 등록하고 executionId를 발급받는다. */
    int insertExecution(QueryExecution execution);

    /** 대기 상태의 실행을 시작한다. 반환값이 1일 때만 SQL을 실행한다. */
    int markRunning(@Param("executionId") Long executionId);

    /** 실행 중인 이력에 성공 또는 실패 결과를 기록한다. */
    int finishExecution(QueryExecution execution);
    
    /** 실행 이력 ID로 SQL 원문과 관련 상태를 조회한다. */
    QueryExecutionTarget selectExecutionTarget(@Param("executionId") Long executionId);
}