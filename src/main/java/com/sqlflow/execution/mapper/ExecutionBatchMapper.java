package com.sqlflow.execution.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;

@Mapper
public interface ExecutionBatchMapper {

    /** 실행 시작 전 묶음을 등록하고, 발급한 ID를 batch 객체에 설정한다. */
    int insertBatch(ExecutionBatch batch);

    /**
     * 예상한 현재 상태일 때만 상태를 변경한다.
     * 반환값이 0이면 대상이 없거나 다른 작업이 이미 상태를 변경한 것이다.
     */
    int updateStatus(
            @Param("batchId") Long batchId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus);
    
    /** 요청 행을 잠그고 실행 판단에 필요한 상태를 함께 조회한다. */
    ExecutionRequestContext selectRequestContextForUpdate(@Param("requestId") Long requestId);

    /** 이미 생성된 실행 묶음이 있는지 확인한다. */
    int countBatchesByRequestId(@Param("requestId") Long requestId);
    
    /** 요청에 등록된 SQL ID를 작성 순서대로 조회한다. */
    List<Long> selectQueryIdsByRequestId(@Param("requestId") Long requestId);

    /** 활성 DB팀 사용자만 수동 실행 대기 묶음을 등록할 수 있는지 확인한다. */
    boolean isActiveDbTeamMember(@Param("userId") Long userId);
    
    /** 실행 묶음의 요청 ID와 현재 상태를 조회한다. */
    ExecutionBatch selectBatch(@Param("batchId") Long batchId);

    /** 요청에 저장된 SQL 순서대로 실행 이력을 조회한다. */
    List<QueryExecution> selectExecutions(@Param("batchId") Long batchId);

    /** 현재 DB에 저장된 계정 활성 상태와 DB_APPROVER 역할을 확인한다. */
    boolean isActiveDbApprover(@Param("userId") Long userId);

    /** 대기 묶음만 시작하고 실제 실행 시작자를 기록한다. */
    int markBatchRunning(
            @Param("batchId") Long batchId,
            @Param("userId") Long userId);

    /** 예상 상태일 때만 요청의 실행 상태를 변경한다. */
    int updateRequestStatus(
            @Param("requestId") Long requestId,
            @Param("expectedStatus") String expectedStatus,
            @Param("nextStatus") String nextStatus);
}
