package com.sqlflow.mysql.dto;

import lombok.Getter;
import lombok.Setter;

/** 실제 MySQL 뷰의 컬럼 구조를 화면 표시 순서와 함께 제공한다. */
@Getter
@Setter
public class MySqlViewColumnDTO {

    private Integer ordinalPosition;
    private String columnName;
    private String columnType;
    private String isNullable;
    private String columnComment;
}