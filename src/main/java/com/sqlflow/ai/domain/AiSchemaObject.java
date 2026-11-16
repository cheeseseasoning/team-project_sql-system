package com.sqlflow.ai.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSchemaObject {

    private String databaseName;
    private String objectName;
    private String objectType;
    private String resourceName;
    private String resourceDescription;
    private String objectComment;
    private List<Column> columns;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Column {
        private String name;
        private String dataType;
    }
}
