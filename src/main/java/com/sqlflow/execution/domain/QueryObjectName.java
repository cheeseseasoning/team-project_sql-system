package com.sqlflow.execution.domain;

/**
 * DB에서 확인한 실제 조회 대상.
 * owner는 Oracle의 스키마 또는 MySQL의 데이터베이스 이름이다.
 */
public record QueryObjectName(String owner, String objectName) {
}