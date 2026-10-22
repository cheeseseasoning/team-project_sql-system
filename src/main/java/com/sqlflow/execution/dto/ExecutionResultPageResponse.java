package com.sqlflow.execution.dto;

import java.util.List;

public record ExecutionResultPageResponse(
        List<ExecutionResultListResponse> items,
        int page,
        int size,
        long totalCount,
        long totalPages) {
}