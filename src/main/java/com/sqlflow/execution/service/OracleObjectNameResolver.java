package com.sqlflow.execution.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;

/**
 * 추출한 객체 이름을 메타데이터 조회용 스키마·객체명으로 변환한다.
 * 동의어의 실제 대상 조회는 별도 단계에서 처리한다.
 */
@Component
public class OracleObjectNameResolver {

    // 따옴표 없는 식별자 또는 큰따옴표로 감싼 식별자를 구분한다.
    private static final String IDENTIFIER = "(?:[\\p{L}][\\p{L}\\p{N}_$#]*|\"[^\"\\x00]+\")";

    // 객체명 또는 스키마.객체명 형식만 받는다.
    private static final Pattern OBJECT_NAME_PATTERN = Pattern.compile(
            "^\\s*(?:(" + IDENTIFIER + ")\\s*\\.\\s*)?(" + IDENTIFIER + ")\\s*$");

    /**
     * 스키마가 생략되면 DB에서 조회한 현재 스키마를 사용한다.
     * currentSchema는 데이터 사전 기준 이름이므로 대소문자를 변경하지 않는다.
     */
    public OracleObjectName resolve(String extractedName, String currentSchema) {

        if (extractedName == null || extractedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 대상 이름이 없습니다.");
        }

        Matcher matcher = OBJECT_NAME_PATTERN.matcher(extractedName);

        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 조회 대상 이름 형식입니다.");
        }

        String schemaPart = matcher.group(1);
        String objectPart = matcher.group(2);

        if (schemaPart == null && (currentSchema == null || currentSchema.isBlank())) {
            throw new IllegalStateException("현재 Oracle 스키마 정보를 확인할 수 없습니다.");
        }

        String owner = schemaPart == null ? currentSchema : normalizeIdentifier(schemaPart);
        String objectName = normalizeIdentifier(objectPart);

        return new OracleObjectName(owner, objectName);
    }

    /** 큰따옴표는 제거하되 내부 대소문자는 유지하고, 일반 이름은 대문자로 변환한다. */
    private String normalizeIdentifier(String identifier) {

        if (identifier.startsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1);
        }

        return identifier.toUpperCase(Locale.ROOT);
    }
    
    /** SQL에서 스키마를 직접 지정했는지 확인한다. */
    public boolean hasExplicitSchema(String extractedName) {

        if (extractedName == null || extractedName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 대상 이름이 없습니다.");
        }

        Matcher matcher = OBJECT_NAME_PATTERN.matcher(extractedName);

        if (!matcher.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 조회 대상 이름 형식입니다.");
        }

        return matcher.group(1) != null;
    }
}