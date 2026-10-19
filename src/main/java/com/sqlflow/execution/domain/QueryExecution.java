package com.sqlflow.execution.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * SQL 한 개의 실행 이력.
 * 같은 SQL을 재실행하면 기존 기록을 수정하지 않고 새 기록을 추가한다.
 */
@Getter
@Setter
public class QueryExecution {

    private Long executionId;
    private Long batchId;
    private Long queryId;

    // 최초 실행은 null, 재실행은 이전 실행 이력의 ID를 저장한다.
    private Long retryOfExecutionId;
    private Integer attemptNo;

    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    private String errorCode;
    private String errorMessage;
}