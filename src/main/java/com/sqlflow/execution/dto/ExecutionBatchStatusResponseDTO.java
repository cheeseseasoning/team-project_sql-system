package com.sqlflow.execution.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/** 실행 상태와 SQL별 처리 개수를 화면에 전달한다. */
@Getter
@Setter
public class ExecutionBatchStatusResponseDTO {

    private Long batchId;
    private Long requestId;

    private String batchStatus;
    private String requestStatus;

    private Integer totalCount;
    private Integer pendingCount;
    private Integer runningCount;
    private Integer successCount;
    private Integer failureCount;

    private LocalDateTime finishedAt;
}