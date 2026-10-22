package com.sqlflow.execution.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ExecutionQueueDetailResponse {
    private Long requestId;
    private String requestNo;
    private String title;
    private String purpose;
    private String detailContent;
    private String drafterName;
    private String deptName;
    private String requestStatus;
    private String approvalStatus;
    private LocalDateTime submittedAt;
    private Long batchId;
    private String batchStatus;
    private List<ExecutionQueueSqlResponse> sqlItems;
}
