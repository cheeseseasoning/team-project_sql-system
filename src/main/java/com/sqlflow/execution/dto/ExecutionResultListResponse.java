package com.sqlflow.execution.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExecutionResultListResponse {

    // 상세 이동과 원래 요청 이동에 사용할 식별자
    private Long executionId;
    private Long queryId;
    private Long requestId;

    private String requestNo;
    private String requestTitle;
    private String queryTitle;

    private Long batchId;
    private String status;
    private Long rowCount;

    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private Long durationMs;
}