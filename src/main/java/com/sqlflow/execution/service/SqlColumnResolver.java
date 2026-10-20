package com.sqlflow.execution.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.mapper.OracleMetadataMapper;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * 같은 SELECT 안에서 테이블 별칭과 실제 컬럼을 연결한다.
 * JOIN 대상 수집, 집계 규칙, 사용자 접근 권한은 별도로 검사한다.
 */
@Component
@RequiredArgsConstructor
public class SqlColumnResolver {

    private final OracleMetadataMapper metadataMapper;

    /**
     * FROM·JOIN의 테이블 목록과 실제 Oracle 대상 정보로 검사 범위를 만든다.
     * resolvedObjects는 OracleTableValidationService의 반환값을 사용한다.
     */
    public Scope createScope(List<Table> tables,
            Map<String, OracleObjectName> resolvedObjects) {

        if (tables == null || tables.isEmpty() || resolvedObjects == null) {
            throw new IllegalArgumentException("테이블 목록과 실제 대상 정보가 필요합니다.");
        }

        Map<String, Binding> sources = new LinkedHashMap<>();

        // 요청마다 새로 구성한다. 전역 캐시로 권한·스키마 변경을 놓치지 않는다.
        Map<OracleObjectName, List<String>> columnCache = new HashMap<>();

        for (Table table : tables) {
            String sourceName = normalizeIdentifier(
                    table.getAlias() == null
                            ? table.getName()
                            : table.getAlias().getName());

            if (sources.containsKey(sourceName)) {
                throw badRequest("테이블 이름 또는 별칭이 중복됩니다: " + sourceName);
            }

            OracleObjectName target =
                    resolvedObjects.get(table.getFullyQualifiedName());

            if (target == null) {
                throw badRequest("실제 조회 대상을 확인할 수 없습니다: " + table.getFullyQualifiedName());
            }

            // 같은 테이블을 서로 다른 별칭으로 JOIN해도 메타데이터는 재사용한다.
            List<String> names = columnCache.computeIfAbsent(
                    target, this::loadColumnNames);

            sources.put(sourceName, new Binding(
                    sourceName, target, names, Set.copyOf(names)));
        }

        return new Scope(sources);
    }

    /** 컬럼 참조를 실제 대상과 컬럼 이름으로 연결한다. */
    public ResolvedColumn resolveColumn(Column column, Scope scope) {

        if (column == null || scope == null) {
            throw new IllegalArgumentException("컬럼과 검사 범위가 필요합니다.");
        }

        String columnName = normalizeIdentifier(column.getColumnName());
        Table qualifier = column.getTable();

        if (hasQualifier(qualifier)) {
            Binding binding = findBinding(qualifier, scope);

            if (!binding.columnSet().contains(columnName)) {
                throw badRequest("조회 대상에 존재하지 않는 컬럼입니다: " + column.getFullyQualifiedName());
            }

            return resolved(binding, columnName);
        }

        Binding matched = null;

        for (Binding binding : scope.sources.values()) {
            if (!binding.columnSet().contains(columnName)) {
                continue;
            }

            if (matched != null) {
                throw badRequest("여러 테이블에 있는 컬럼입니다. 테이블 별칭을 지정하세요: " + column.getColumnName());
            }

            matched = binding;
        }

        if (matched == null) {
            throw badRequest("조회 대상에 존재하지 않는 컬럼입니다: "
                    + column.getColumnName());
        }

        return resolved(matched, columnName);
    }

    /**
     * '*'는 qualifier에 null, 'U.*'는 U를 나타내는 Table을 전달한다.
     * 원본 SQL을 변경하지 않고 검사용 컬럼 참조 목록만 반환한다.
     */
    public List<ResolvedColumn> expandWildcard(Table qualifier, Scope scope) {

        if (scope == null) {
            throw new IllegalArgumentException("검사 범위가 필요합니다.");
        }

        List<ResolvedColumn> result = new ArrayList<>();

        if (qualifier != null) {
            appendColumns(findBinding(qualifier, scope), result);
        } else {
            for (Binding binding : scope.sources.values()) {
                appendColumns(binding, result);
            }
        }

        return List.copyOf(result);
    }

    /** Oracle에서 받은 실제 이름과 컬럼 순서를 유지한다. */
    private List<String> loadColumnNames(OracleObjectName target) {

        List<OracleColumnMetadata> columns =
                metadataMapper.selectColumns(target.owner(), target.objectName());

        if (columns.isEmpty()) {
            throw badRequest("조회 대상의 컬럼 정보를 확인할 수 없습니다: "
                    + target.owner() + "." + target.objectName());
        }

        return columns.stream()
                .map(OracleColumnMetadata::getColumnName)
                .collect(Collectors.toUnmodifiableList());
    }

    /** 컬럼 앞의 테이블명 또는 별칭에 해당하는 대상을 찾는다. */
    private Binding findBinding(Table qualifier, Scope scope) {

        // 현재는 U.COLUMN, USERS.COLUMN 형태의 한 단계 접두어를 지원한다.
        if (qualifier.getSchemaName() != null
                || !qualifier.getFullyQualifiedName().equals(qualifier.getName())) {
            throw badRequest("스키마를 포함한 컬럼 접두어는 아직 지원하지 않습니다.");
        }

        String sourceName = normalizeIdentifier(qualifier.getName());
        Binding binding = scope.sources.get(sourceName);

        if (binding == null) {
            throw badRequest("선언되지 않은 테이블 이름 또는 별칭입니다: " + qualifier.getFullyQualifiedName());
        }

        return binding;
    }

    private boolean hasQualifier(Table qualifier) {
        return qualifier != null
                && qualifier.getFullyQualifiedName() != null
                && !qualifier.getFullyQualifiedName().isBlank();
    }

    /** 같은 실제 테이블도 별칭이 다르면 서로 다른 참조로 유지한다. */
    private ResolvedColumn resolved(Binding binding, String columnName) {
        return new ResolvedColumn(
                binding.sourceName(), binding.target(), columnName);
    }

    private void appendColumns(Binding binding, List<ResolvedColumn> result) {
        for (String columnName : binding.columnNames()) {
            result.add(resolved(binding, columnName));
        }
    }

    /** SQL에서 받은 이름만 정규화하고, DB에서 받은 실제 이름은 변경하지 않는다. */
    private String normalizeIdentifier(String identifier) {

        if (identifier == null || identifier.isBlank()) {
            throw badRequest("테이블·별칭 또는 컬럼 이름이 없습니다.");
        }

        if (identifier.length() >= 2
                && identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier.substring(1, identifier.length() - 1)
                    .replace("\"\"", "\"");
        }

        return identifier.toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** 한 SELECT의 대상 정보다. 요청 간 공유하지 않는다. */
    public static final class Scope {

        private final Map<String, Binding> sources;

        private Scope(Map<String, Binding> sources) {
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
        }
    }

    /** SQL의 한 테이블 참조와 그 실제 대상·컬럼 정보. */
    private record Binding(
            String sourceName,
            OracleObjectName target,
            List<String> columnNames,
            Set<String> columnSet) {
    }

    /** GROUP BY 비교와 대상별 접근 정책 검사에 사용할 컬럼 식별 정보. */
    public record ResolvedColumn(
            String sourceName,
            OracleObjectName target,
            String columnName) {
    }
}