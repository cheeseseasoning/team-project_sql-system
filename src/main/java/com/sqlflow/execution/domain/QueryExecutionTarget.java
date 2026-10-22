package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 실행할 SQL 원문과 실행 가능 여부 판단에 필요한 상태를 담는다.
 * 여러 테이블을 JOIN한 조회 결과를 Service에 전달한다.
 */
@Getter
@Setter
public class QueryExecutionTarget {

    private Long executionId;
    private Long batchId;
    private Long requestId;
    private Long queryId;
    
    // 실행자의 ID가 아닌, 조회 요청을 작성한 사용자의 ID.
    private Long drafterId;

    private String executionStatus;
    private String batchStatus;
    private String requestStatus;
    private String approvalStatus;

    private String sqlText;
}