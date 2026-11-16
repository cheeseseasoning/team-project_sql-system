package com.sqlflow.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlGenerationRequest {

    @NotNull
    private SqlType sqlType;

    @NotBlank
    @Size(max = 3000)
    private String prompt;

    public enum SqlType {
        SELECT,
        CREATE,
        INSERT,
        UPDATE,
        DELETE
    }
}