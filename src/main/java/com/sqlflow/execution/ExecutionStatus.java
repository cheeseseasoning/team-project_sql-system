package com.sqlflow.execution;

/**
 * SQL 실행 상태값을 관리한다.
 * 실행 묶음과 개별 SQL에서 사용하며, PARTIAL_FAILED는 실행 묶음에만 사용한다.
 */
public enum ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    PARTIAL_FAILED
}
