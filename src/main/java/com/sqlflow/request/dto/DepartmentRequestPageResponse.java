package com.sqlflow.request.dto;

import java.util.List;

public record DepartmentRequestPageResponse(
        List<DepartmentRequestListResponse> items,
        int page,
        int size,
        long totalCount,
        long totalPages) {
}