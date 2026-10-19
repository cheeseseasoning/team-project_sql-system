package com.sqlflow.request.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestSummaryResponse {

    private long totalCount;
    private long draftCount;
    private long pendingApprovalCount;
    private long readyCount;
    private long runningCount;
    private long retryReviewCount;
    private long completedCount;
    private long partialFailedCount;
    private long failedCount;
    private long rejectedCount;
    private long withdrawnCount;
}