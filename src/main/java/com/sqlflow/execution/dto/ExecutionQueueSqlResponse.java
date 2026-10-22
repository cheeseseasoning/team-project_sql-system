package com.sqlflow.execution.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ExecutionQueueSqlResponse {
    private Long queryId;
    private Integer queryOrder;
    private String queryTitle;
    private String sqlText;
    private Long executionId;
    private String executionStatus;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;
}
