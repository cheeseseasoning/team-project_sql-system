package com.sqlflow.ai.service;

import org.springframework.stereotype.Service;

import com.sqlflow.ai.dto.SqlGenerationRequest.SqlType;
import com.sqlflow.execution.service.SqlBasicValidator;

import lombok.RequiredArgsConstructor;

/** AI 초안 반환 전 기본 형식만 검사한다. 요청 검증이나 제출 검증을 대신하지 않는다. */
@Service
@RequiredArgsConstructor
public class AiGeneratedSqlValidationService {

    private final SqlBasicValidator basicValidator;

    /**
     * 기존 호출 형식은 유지한다.
     * 사용자 권한과 EXPLAIN은 기존 요청 검증 경로에서 별도로 확인한다.
     */
    public String validate(SqlType type, String sql, Long userId) {
        if (type == null) return "SQL 유형을 선택하세요.";

        var result = basicValidator.validate(type.name(), sql);
        if (result.blocked()) {
            return "생성된 SQL의 기본 형식을 확인하지 못했습니다. " + result.warning();
        }
        return null;
    }
}