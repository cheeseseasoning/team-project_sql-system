package com.sqlflow.mysql.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.SqlTableExtractor;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.statement.select.Select;

/**
 * SQL에 적힌 테이블·뷰 이름을 MySQL의 실제 조회 대상으로 연결한다.
 * 컬럼 검사, SQL 지원 범위 검사, 직원별 접근 정책은 별도로 수행한다.
 */
@Service
@RequiredArgsConstructor
public class MySqlTableValidationService {

    // 일반 식별자와 백틱으로 감싼 식별자를 지원한다.
    private static final String IDENTIFIER =
            "(?:[\\p{L}_$][\\p{L}\\p{N}_$]*|`(?:``|[^`])+`)";

    private static final Pattern OBJECT_NAME = Pattern.compile(
            "^\\s*(?:(" + IDENTIFIER + ")\\s*\\.\\s*)?("
                    + IDENTIFIER + ")\\s*$");

    private final SqlTableExtractor tableExtractor;
    private final MySqlMetadataMapper metadataMapper;

    /**
     * SQL의 참조 이름과 실제 DB·객체명의 대응 관계를 반환한다.
     * 예: connection_test → sqlflow_demo.connection_test
     */
    @Transactional(transactionManager = "queryTransactionManager", readOnly = true)
    public Map<String, QueryObjectName> validateTables(Select select) {

        if (select == null) {
            throw badRequest("분석된 SELECT 정보가 없습니다.");
        }

        String currentDatabase = metadataMapper.selectCurrentDatabase();

        if (currentDatabase == null || currentDatabase.isBlank()) {
            throw new IllegalStateException("MySQL의 기본 데이터베이스를 확인할 수 없습니다.");
        }

        Map<String, QueryObjectName> result = new LinkedHashMap<>();

        for (String extractedName : tableExtractor.extractTables(select)) {
            var matcher = OBJECT_NAME.matcher(extractedName);

            if (!matcher.matches()) {
                throw badRequest("지원하지 않는 MySQL 조회 대상 이름입니다: " + extractedName);
            }

            String databaseName = matcher.group(1) == null ? currentDatabase : unquote(matcher.group(1));

            String objectName = unquote(matcher.group(2));

            var metadata = metadataMapper.selectObject(databaseName, objectName);

            if (metadata == null) {
                throw badRequest("조회 대상을 찾을 수 없거나 DB 계정에서 확인할 수 없습니다: " + extractedName);
            }

            // 정책 검사에도 입력 문자열이 아닌 실제 등록 이름을 사용한다.
            result.put(
                    extractedName,
                    new QueryObjectName(
                            metadata.getDatabaseName(),
                            metadata.getObjectName()));
        }

        return Map.copyOf(result);
    }

    /** 백틱을 제거하되 이름의 대소문자는 변경하지 않는다. */
    private String unquote(String identifier) {

        if (identifier.startsWith("`") && identifier.endsWith("`")) {
            return identifier.substring(1, identifier.length() - 1).replace("``", "`");
        }

        return identifier;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}