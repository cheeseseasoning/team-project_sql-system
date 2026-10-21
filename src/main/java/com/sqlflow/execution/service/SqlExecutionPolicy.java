package com.sqlflow.execution.service;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 개발 초기 실행 테스트에 사용할 SQL을 제한한다.
 * JSqlParser와 Oracle 메타데이터 검증을 연결하기 전의 임시 정책이다.
 */
@Component
public class SqlExecutionPolicy {

    private static final Set<String> ALLOWED_SQLS = Set.of(
            "SELECT 1 FROM DUAL",
            "SELECT SYSDATE FROM DUAL"
    );

    /**
     * 저장된 SQL이 허용 목록과 일치하는지 확인하고 실행할 문자열을 반환한다.
     * 문자열 내부를 변경하지 않고 양 끝 공백만 제거한다.
     */
    public String validate(String sqlText) {

        if (sqlText == null || sqlText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "실행할 SQL이 없습니다.");
        }

        String sql = sqlText.strip();

        boolean allowed = ALLOWED_SQLS.stream()
                .anyMatch(allowedSql -> allowedSql.equalsIgnoreCase(sql));

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 개발 단계에서는 사전에 허용한 테스트 SQL만 실행할 수 있습니다.");
        }

        return sql;
    }
}