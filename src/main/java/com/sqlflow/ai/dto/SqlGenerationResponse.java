package com.sqlflow.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SqlGenerationResponse {

    private String sql;
    private String explanation;
    private boolean needsClarification;
}
