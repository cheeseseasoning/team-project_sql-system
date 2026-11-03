package com.sqlflow.execution.mapper;

import java.util.List;

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

    /** Release a reservation only when the background executor rejected it. */
    int resetRejectedReservation(@Param("executionId") Long executionId);

    /** 실행 중인 이력에 성공 또는 실패 결과를 기록한다. */
    int finishExecution(QueryExecution execution);
    
    /** 실행 이력 ID로 SQL 원문과 관련 상태를 조회한다. */
    QueryExecutionTarget selectExecutionTarget(@Param("executionId") Long executionId);
    
    /** 선택한 SQL의 기존 실행 이력 개수. */
    int countExecutionsByQueryId(
            @Param("queryId") Long queryId);

    /** 같은 기안서에 접수됐거나 실행 중인 SQL 이력 개수. */
    int countActiveExecutionsByRequestId(
            @Param("requestId") Long requestId);
    
    /** 기안서에 남아 있는 전체 SQL 실행 이력 개수. */
    int countExecutionsByRequestId(
            @Param("requestId") Long requestId);

    /** 실행기 접수가 거절된 배치 없는 예약만 제거한다. */
    int deleteRejectedStandaloneReservation(
            @Param("requestId") Long requestId,
            @Param("queryId") Long queryId,
            @Param("executionId") Long executionId);
    
    /**
     * 기안서의 SQL마다 최신 실행 이력을 조회한다.
     * 미실행 SQL도 포함하며, 이 경우 실행 ID와 상태는 null이다.
     */
    List<QueryExecution> selectLatestExecutionsByRequestId(
            @Param("requestId") Long requestId);
}
