package com.sqlflow.request.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record SelectSqlValidationRequest(
        @NotEmpty(message = "검증할 SQL을 입력하세요.")
        @Size(max = 5, message = "SQL은 최대 5개까지 검증할 수 있습니다.")
        List<@NotBlank @Size(max = 20000) String> sqls
) {
}