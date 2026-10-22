package com.sqlflow.execution.dto;

import java.util.List;

public record ExecutionQueuePageResponse(
        List<ExecutionQueueItemResponse> items, int page, int size,
        long totalCount, int totalPages) { }
