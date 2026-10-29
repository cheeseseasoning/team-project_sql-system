package com.sqlflow.request.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 뷰 생성 SQL 한 항목의 기본 검사 결과.
 * 실제 DB 실행 성공이나 객체·컬럼 존재 여부를 보장하지 않는다.
 */
@Getter
@AllArgsConstructor
public class ViewCreateSqlValidationResultDTO {

    private final Status status;
    private final String message;

    public enum Status {
        VALID,
        WARNING,
        BLOCKED
    }
}