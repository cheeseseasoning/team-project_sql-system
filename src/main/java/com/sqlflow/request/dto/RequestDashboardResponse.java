package com.sqlflow.request.dto;

import java.util.List;

public record RequestDashboardResponse(
        RequestSummaryResponse summary,
        List<RequestListResponse> recentRequests) {
}