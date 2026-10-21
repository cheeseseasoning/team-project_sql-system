package com.sqlflow.execution.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.SqlColumnResolver.Scope;
import com.sqlflow.execution.service.SqlSelectExpressionAnalyzer.Analysis;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * SELECT 항목과 GROUP BY·ORDER BY의 관계를 검사한다.
 * FROM·JOIN 구조, ON·WHERE, 실행 권한은 다른 검사에서 처리한다.
 */
@Component
@RequiredArgsConstructor
public class SqlGroupingOrderValidator {

    private final SqlColumnResolver columnResolver;
    private final SqlSelectExpressionAnalyzer expressionAnalyzer;

    /** SELECT 분석 결과를 재사용해 그룹·정렬 규칙을 검사한다. */
    public Result validate(PlainSelect select, Scope scope) {

        if (select == null || scope == null) {
            throw new IllegalArgumentException("SELECT와 검사 범위가 필요합니다.");
        }

        if (select.getSelectItems() == null || select.getSelectItems().isEmpty()) {
            throw badRequest("조회할 SELECT 항목이 없습니다.");
        }

        // DISTINCT 조회에는 별도의 ORDER BY 제약 등이 있으므로 후속 지원한다.
        if (select.getDistinct() != null) {
            throw badRequest("SELECT DISTINCT는 아직 지원하지 않습니다.");
        }

        if (select.getHaving() != null) {
            throw badRequest("HAVING은 아직 지원하지 않습니다.");
        }

        List<Analysis> selectAnalyses = new ArrayList<>();
        Map<String, List<Analysis>> aliases = new HashMap<>();
        Set<ResolvedColumn> references = new LinkedHashSet<>();

        for (var item : select.getSelectItems()) {
            Analysis analysis = expressionAnalyzer.analyze(item.getExpression(), scope);

            selectAnalyses.add(analysis);
            references.addAll(analysis.referencedColumns());

            if (item.getAlias() != null) {
                if (item.getExpression() instanceof AllColumns) {
                    throw badRequest("별표 SELECT 항목에는 출력 별칭을 지정할 수 없습니다.");
                }

                String alias = normalizeIdentifier(item.getAlias().getName());

                aliases.computeIfAbsent(alias, key -> new ArrayList<>()).add(analysis);
            }
        }

        Set<ResolvedColumn> groupColumns = resolveGroupColumns(select, scope);
        references.addAll(groupColumns);

        List<Analysis> orderAnalyses = resolveOrderExpressions(select, scope, aliases);

        for (Analysis analysis : orderAnalyses) {
            references.addAll(analysis.referencedColumns());
        }

        // ORDER BY에만 집계가 있더라도 그룹 조회 규칙을 확인한다.
        boolean grouped = select.getGroupBy() != null
                || selectAnalyses.stream().anyMatch(Analysis::aggregate)
                || orderAnalyses.stream().anyMatch(Analysis::aggregate);

        if (grouped) {
            validateGroupedColumns(selectAnalyses, groupColumns, "SELECT");
            validateGroupedColumns(orderAnalyses, groupColumns, "ORDER BY");
        }

        return new Result(selectAnalyses, references, grouped);
    }

    /** GROUP BY는 실제 컬럼만 지원하며 출력 별칭·함수는 허용하지 않는다. */
    private Set<ResolvedColumn> resolveGroupColumns(PlainSelect select, Scope scope) {

        if (select.getGroupBy() == null) {
            return Set.of();
        }

        var groupBy = select.getGroupBy();

        if (groupBy.isMysqlWithRollup()
                || (groupBy.getGroupingSets() != null
                    && !groupBy.getGroupingSets().isEmpty())) {
            throw badRequest("ROLLUP·GROUPING SETS는 아직 지원하지 않습니다.");
        }

        var expressions = groupBy.getGroupByExpressions();

        if (expressions == null || expressions.isEmpty()) {
            throw badRequest("GROUP BY 컬럼을 확인할 수 없습니다.");
        }

        Set<ResolvedColumn> columns = new LinkedHashSet<>();

        for (Object expression : expressions) {
            if (!(expression instanceof Column column)) {
                throw badRequest("GROUP BY에는 일반 컬럼만 사용할 수 있습니다.");
            }

            columns.add(columnResolver.resolveColumn(column, scope));
        }

        return Set.copyOf(columns);
    }

    /** ORDER BY의 출력 별칭을 먼저 해석하고, 나머지는 실제 표현식으로 검사한다. */
    private List<Analysis> resolveOrderExpressions(PlainSelect select,
            Scope scope, Map<String, List<Analysis>> aliases) {

        if (select.getOrderByElements() == null) {
            return List.of();
        }

        List<Analysis> results = new ArrayList<>();

        for (var order : select.getOrderByElements()) {
            if (order.isMysqlWithRollup()) {
                throw badRequest("ORDER BY의 ROLLUP 옵션은 지원하지 않습니다.");
            }

            Expression expression = order.getExpression();

            if (expression instanceof Column column && hasNoQualifier(column)) {
                String name = normalizeIdentifier(column.getColumnName());
                List<Analysis> candidates = aliases.get(name);

                if (candidates != null) {
                    if (candidates.size() != 1) {
                        throw badRequest("ORDER BY의 출력 별칭이 중복됩니다: " + column.getColumnName());
                    }

                    results.add(candidates.get(0));
                    continue;
                }
            }

            // 별표·순번·계산식 정렬은 별도 해석 없이 허용하지 않는다.
            if (!(expression instanceof Column)
                    && !(expression instanceof Function)) {
                throw badRequest("ORDER BY는 컬럼·출력 별칭·기본 집계 함수만 지원합니다.");
            }

            results.add(expressionAnalyzer.analyze(expression, scope));
        }

        return List.copyOf(results);
    }

    /** 그룹 조회에서 집계 밖의 컬럼은 GROUP BY에 포함되어야 한다. */
    private void validateGroupedColumns(List<Analysis> analyses,
            Set<ResolvedColumn> groupColumns, String clause) {

        for (Analysis analysis : analyses) {
            for (ResolvedColumn column : analysis.nonAggregateColumns()) {
                if (!groupColumns.contains(column)) {
                    throw badRequest(clause + "의 비집계 컬럼이 GROUP BY에 없습니다: "
                            + column.sourceName() + "." + column.columnName());
                }
            }
        }
    }

    private boolean hasNoQualifier(Column column) {
        return column.getTable() == null
                || column.getTable().getFullyQualifiedName() == null
                || column.getTable().getFullyQualifiedName().isBlank();
    }

    private String normalizeIdentifier(String name) {
        if (name == null || name.isBlank()) {
            throw badRequest("출력 별칭을 확인할 수 없습니다.");
        }

        if (name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
            return name.substring(1, name.length() - 1).replace("\"\"", "\"");
        }

        return name.toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** 전체 검증에서 재사용할 SELECT 분석과 참조 컬럼 정보. */
    public record Result(
            List<Analysis> selectItems,
            Set<ResolvedColumn> referencedColumns,
            boolean grouped) {

        public Result {
            selectItems = List.copyOf(selectItems);
            referencedColumns = Set.copyOf(referencedColumns);
        }
    }
}