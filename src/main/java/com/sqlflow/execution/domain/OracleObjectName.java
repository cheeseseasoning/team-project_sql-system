package com.sqlflow.execution.domain;

/** Oracle 메타데이터 조회에 사용할 스키마와 객체명. */
public record OracleObjectName(String owner, String objectName) {
}