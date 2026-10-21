package com.sqlflow.execution.service;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * 단일 테이블 조회에서 컬럼의 테이블명·별칭을 검사한다.
 * 컬럼 자체의 존재 여부와 Oracle 객체 확인은 별도로 처리한다.
 */
@Component
public class SqlColumnQualifierValidator {

    /**
     * column은 검사할 컬럼, fromTable은 같은 SELECT의 FROM 대상이다.
     * 호출하는 쪽에서 JOIN·서브쿼리 없는 단일 테이블인지 먼저 확인해야 한다.
     */
    public void validateQualifier(Column column, Table fromTable) {

        if (column == null || fromTable == null) {
            throw new IllegalArgumentException("검사할 컬럼과 FROM 테이블이 필요합니다.");
        }

        Table qualifier = column.getTable();

        // USER_NAME처럼 접두어가 없으면 단일 FROM 테이블의 컬럼으로 처리한다.
        if (qualifier == null || qualifier.getFullyQualifiedName() == null
                || qualifier.getFullyQualifiedName().isBlank()) {
            return;
        }

        String qualifierName = qualifier.getName();

        // SQLFLOW.USERS.USER_NAME 같은 다단계 접두어는 후속 단계에서 지원한다.
        if (qualifierName == null
                || !qualifier.getFullyQualifiedName().equals(qualifierName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마를 포함한 컬럼 접두어는 아직 지원하지 않습니다.");
        }

        // 별칭을 지정했다면 원래 테이블 이름 대신 그 별칭으로 참조해야 한다.
        String expectedName = fromTable.getAlias() == null
                ? fromTable.getName()
                : fromTable.getAlias().getName();

        if (expectedName == null || expectedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FROM 대상의 테이블명 또는 별칭을 확인할 수 없습니다.");
        }

        if (!normalizeIdentifier(expectedName).equals(normalizeIdentifier(qualifierName))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FROM 대상과 일치하지 않는 컬럼 접두어입니다: " + qualifierName);
        }
    }

    /** Oracle 식별자 규칙에 맞춰 비교하고, 큰따옴표 안의 대소문자는 유지한다. */
    private String normalizeIdentifier(String identifier) {

        if (identifier.length() >= 2 && identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1).replace("\"\"", "\"");
        }

        return identifier.toUpperCase(Locale.ROOT);
    }
}