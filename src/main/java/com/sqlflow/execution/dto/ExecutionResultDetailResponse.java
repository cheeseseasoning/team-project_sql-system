package com.sqlflow.execution.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutionResultDetailResponse {

    private Long executionId;
    private Long batchId;
    private Long queryId;
    private Long requestId;

    private String requestNo;
    private String requestTitle;
    private String queryTitle;
    private String sqlText;

    private Integer attemptNo;
    private String status;

    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;

    private String errorCode;
    private String errorMessage;

    private Long resultId;
    private Long rowCount;
    private Long fileSize;
    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;
}