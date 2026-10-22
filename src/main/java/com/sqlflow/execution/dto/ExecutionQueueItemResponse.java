package com.sqlflow.execution.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ExecutionQueueItemResponse {
    private Long requestId;
    private String requestNo;
    private String title;
    private String drafterName;
    private String deptName;
    private String requestStatus;
    private LocalDateTime submittedAt;
    private Long queryCount;
    private Long batchId;
    private String batchStatus;
}
