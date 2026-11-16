package com.sqlflow.ai.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI에 전달하는 객체·컬럼 구조이며, 실제 행 데이터는 포함하지 않는다. */
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

        // SQL에는 실제 DB 컬럼명인 name을 사용한다.
        private String name;
        private String dataType;

        // 자연어 요청의 업무 의미를 해석하는 참고 정보다.
        private String logicalName;
        private String description;

        /** 기존 컬럼명·타입만 전달하는 호출도 그대로 사용할 수 있다. */
        public Column(String name, String dataType) {
            this.name = name;
            this.dataType = dataType;
        }
    }
}