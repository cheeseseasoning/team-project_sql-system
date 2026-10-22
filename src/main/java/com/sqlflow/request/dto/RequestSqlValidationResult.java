package com.sqlflow.request.dto;

import java.util.List;

/** Validation result for one SQL item in a saved draft. */
public record RequestSqlValidationResult(
        Long queryId,
        String validationStatus,
        String message,
        String accessLevel,
        List<Long> resourceIds) {

    public RequestSqlValidationResult {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
    }
}
