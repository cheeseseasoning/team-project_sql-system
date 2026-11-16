package com.sqlflow.mysql.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * MySQL 테이블·뷰의 실제 컬럼 구조.
 * DB에서 받은 이름의 대소문자를 그대로 유지한다.
 */
@Getter
@Setter
public class MySqlColumnMetadata {

    private String databaseName;
    private String tableName;
    private String columnName;
    private String dataType;
    private Integer ordinalPosition;

    // MySQL COMMENT 원문이며, AI 전달 단계에서 논리명과 설명으로 나눈다.
    private String columnComment;
}