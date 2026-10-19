package com.sqlflow.execution.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 한 조회 요청에 포함된 SQL들을 실행하는 묶음 정보.
 * SQL별 상세 실행 이력은 QUERY_EXECUTION에서 별도로 관리한다.
 */
@Getter
@Setter
public class ExecutionBatch {

    private Long batchId;
    private Long requestId;
    private Long initiatedBy;

    private String triggerType;
    private String batchType;
    private String batchStatus;

    private LocalDateTime requestedAt;
    private LocalDateTime finishedAt;
}