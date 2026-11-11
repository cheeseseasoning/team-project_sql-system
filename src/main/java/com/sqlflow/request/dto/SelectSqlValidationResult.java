package com.sqlflow.request.dto;

public record SelectSqlValidationResult(
        String validationStatus,
        String message
) {
}