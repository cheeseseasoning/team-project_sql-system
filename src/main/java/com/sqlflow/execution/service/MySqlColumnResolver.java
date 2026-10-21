package com.sqlflow.execution.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.domain.MySqlColumnMetadata;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

/**
 * MySQL 조회 대상의 별칭과 실제 컬럼을 연결한다.
 * 컬럼 비교용 이름과 DB에 등록된 실제 이름을 구분한다.
 */
@Component
@RequiredArgsConstructor
public class MySqlColumnResolver {

    private final MySqlMetadataMapper metadataMapper;

    /** 검증된 실제 대상과 컬럼 정보로 한 SELECT의 검사 범위를 구성한다. */
    public Scope createScope(List<Table> tables, Map<String, QueryObjectName> resolvedObjects) {

        if (tables == null || tables.isEmpty() || resolvedObjects == null) {
            throw new IllegalArgumentException("테이블 목록과 실제 대상 정보가 필요합니다.");
        }

        int caseMode = metadataMapper.selectLowerCaseTableNames();

        if (caseMode < 0 || caseMode > 2) {
            throw new IllegalStateException("MySQL의 이름 비교 설정을 확인할 수 없습니다.");
        }

        Map<String, Binding> sources = new LinkedHashMap<>();
        Map<QueryObjectName, List<String>> columnCache = new HashMap<>();

        for (Table table : tables) {
            String sourceName = sourceKey(sourceName(table), caseMode);

            if (sources.containsKey(sourceName)) {
                throw badRequest("테이블 이름 또는 별칭이 중복됩니다: " + sourceName);
            }

            QueryObjectName target = resolvedObjects.get(table.getFullyQualifiedName());

            if (target == null) {
                throw badRequest("실제 조회 대상을 확인할 수 없습니다: " + table.getFullyQualifiedName());
            }

            // 같은 테이블을 여러 별칭으로 참조해도 컬럼 조회는 재사용한다.
            List<String> names = columnCache.computeIfAbsent(target, this::loadColumnNames);

            sources.put(sourceName, new Binding(sourceName, target, names, indexColumns(names)));
        }

        return new Scope(sources, caseMode);
    }

    /**
     * 기존 컬럼 정보를 재사용하면서 현재 JOIN까지의 대상만 허용한다.
     * 뒤에 등장하는 JOIN의 별칭을 앞선 ON에서 사용하는 것을 막는다.
     */
    public Scope restrictScope(Scope scope, List<Table> visibleTables) {

        if (scope == null || visibleTables == null || visibleTables.isEmpty()) {
            throw new IllegalArgumentException("기존 검사 범위와 참조 가능한 테이블이 필요합니다.");
        }

        Map<String, Binding> visibleSources = new LinkedHashMap<>();

        for (Table table : visibleTables) {
            String sourceName = sourceKey(sourceName(table), scope.caseMode);
            Binding binding = scope.sources.get(sourceName);

            if (binding == null) {
                throw badRequest("등록되지 않은 조회 대상입니다: " + sourceName);
            }

            visibleSources.put(sourceName, binding);
        }

        return new Scope(visibleSources, scope.caseMode);
    }

    /** SQL의 컬럼 참조를 실제 대상과 실제 컬럼명으로 연결한다. */
    public ResolvedColumn resolveColumn(Column column, Scope scope) {

        if (column == null || scope == null) {
            throw new IllegalArgumentException("컬럼과 검사 범위가 필요합니다.");
        }

        String columnKey = normalizeColumnName(column.getColumnName());
        Table qualifier = column.getTable();

        if (hasQualifier(qualifier)) {
            Binding binding = findBinding(qualifier, scope);
            String actualName = binding.columnsByKey().get(columnKey);

            if (actualName == null) {
                throw badRequest("조회 대상에 존재하지 않는 컬럼입니다: " + column.getFullyQualifiedName());
            }

            return resolved(binding, actualName);
        }

        Binding matched = null;
        String actualName = null;

        for (Binding binding : scope.sources.values()) {
            String candidate = binding.columnsByKey().get(columnKey);

            if (candidate == null) {
                continue;
            }

            if (matched != null) {
                throw badRequest("여러 테이블에 있는 컬럼입니다. 테이블 별칭을 지정하세요: " + column.getColumnName());
            }

            matched = binding;
            actualName = candidate;
        }

        if (matched == null) {
            throw badRequest("조회 대상에 존재하지 않는 컬럼입니다: " + column.getColumnName());
        }

        return resolved(matched, actualName);
    }

    /** 별표를 실제 컬럼 목록으로 확장하되 원본 SQL은 변경하지 않는다. */
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

    /**
     * MySQL 컬럼명과 출력 별칭의 비교용 키.
     * 실제 컬럼명은 별도로 보존하므로 반환 결과의 이름은 바뀌지 않는다.
     */
    public String normalizeColumnName(String identifier) {
        return unquote(identifier).toUpperCase(Locale.ROOT);
    }

    private List<String> loadColumnNames(QueryObjectName target) {
        List<MySqlColumnMetadata> columns = metadataMapper.selectColumns(target.owner(), target.objectName());

        if (columns.isEmpty()) {
            throw badRequest("조회 대상의 컬럼 정보를 확인할 수 없습니다: " + target.owner() + "." + target.objectName());
        }

        return columns.stream().map(MySqlColumnMetadata::getColumnName).toList();
    }

    /** 비교용 이름과 실제 컬럼명의 대응 관계를 만든다. */
    private Map<String, String> indexColumns(List<String> names) {
        Map<String, String> result = new LinkedHashMap<>();

        for (String name : names) {
            String key = name.toUpperCase(Locale.ROOT);

            if (result.putIfAbsent(key, name) != null) {
                throw badRequest("중복된 컬럼 이름을 확인했습니다: " + name);
            }
        }

        return Map.copyOf(result);
    }

    private Binding findBinding(Table qualifier, Scope scope) {

        if (qualifier.getSchemaName() != null
                || !qualifier.getFullyQualifiedName().equals(qualifier.getName())) {
            throw badRequest("DB명을 포함한 컬럼 접두어는 아직 지원하지 않습니다. 테이블 별칭을 사용하세요.");
        }

        String sourceName = sourceKey(qualifier.getName(), scope.caseMode);
        Binding binding = scope.sources.get(sourceName);

        if (binding == null) {
            throw badRequest("선언되지 않은 테이블 이름 또는 별칭입니다: " + qualifier.getFullyQualifiedName());
        }

        return binding;
    }

    private String sourceName(Table table) {
        return table.getAlias() == null ? table.getName() : table.getAlias().getName();
    }

    /** 테이블·별칭은 서버의 대소문자 처리 설정에 맞춰 비교한다. */
    private String sourceKey(String identifier, int caseMode) {
        String name = unquote(identifier);
        return caseMode == 0 ? name : name.toUpperCase(Locale.ROOT);
    }

    private String unquote(String identifier) {

        if (identifier == null || identifier.isBlank()) {
            throw badRequest("테이블·별칭 또는 컬럼 이름이 없습니다.");
        }

        if (identifier.startsWith("`") && identifier.endsWith("`")) {
            return identifier.substring(1, identifier.length() - 1).replace("``", "`");
        }

        // SQL 모드에 따라 의미가 달라지므로 현재는 백틱 식별자만 지원한다.
        if (identifier.startsWith("\"") || identifier.startsWith("'")) {
            throw badRequest("식별자를 감쌀 때는 MySQL 백틱(`)을 사용하세요.");
        }

        return identifier;
    }

    private boolean hasQualifier(Table qualifier) {
        return qualifier != null
                && qualifier.getFullyQualifiedName() != null
                && !qualifier.getFullyQualifiedName().isBlank();
    }

    private ResolvedColumn resolved(Binding binding, String columnName) {
        return new ResolvedColumn(binding.sourceName(), binding.target(), columnName);
    }

    private void appendColumns(Binding binding, List<ResolvedColumn> result) {
        for (String columnName : binding.columnNames()) {
            result.add(resolved(binding, columnName));
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** 한 SELECT에서만 사용하는 검사 범위. */
    public static final class Scope {

        private final Map<String, Binding> sources;
        private final int caseMode;

        private Scope(Map<String, Binding> sources, int caseMode) {
            this.sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources));
            this.caseMode = caseMode;
        }
    }

    private record Binding(
            String sourceName,
            QueryObjectName target,
            List<String> columnNames,
            Map<String, String> columnsByKey) {
    }

    /** 그룹 규칙 비교와 정책 검사에서 사용할 실제 컬럼 정보. */
    public record ResolvedColumn(String sourceName, QueryObjectName target, String columnName) {
    }
}