package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/** Oracle에서 조회한 테이블·뷰의 컬럼 구조 정보. */
@Getter
@Setter
public class OracleColumnMetadata {

    private String owner;
    private String tableName;
    private String columnName;
    private String dataType;
}