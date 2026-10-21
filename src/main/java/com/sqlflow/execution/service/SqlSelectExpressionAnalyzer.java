package com.sqlflow.execution.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.MySqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.MySqlColumnResolver.Scope;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.AllColumns;
import net.sf.jsqlparser.statement.select.AllTableColumns;

/**
 * SELECT 항목의 컬럼·별표·기본 집계 함수를 분석한다.
 * GROUP BY 적합성, 함수 인자의 자료형, 실행 권한은 별도로 검사한다.
 */
@Component
@RequiredArgsConstructor
public class SqlSelectExpressionAnalyzer {

    private static final Set<String> AGGREGATES =
            Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");

    private final MySqlColumnResolver columnResolver;

    /** SELECT 항목 하나를 분석하여 참조 컬럼과 집계 여부를 반환한다. */
    public Analysis analyze(Expression expression, Scope scope) {

        if (expression == null || scope == null) {
            throw new IllegalArgumentException("분석할 표현식과 검사 범위가 필요합니다.");
        }

        if (expression instanceof Column column) {
            return ordinary(List.of(columnResolver.resolveColumn(column, scope)));
        }

        // AllTableColumns는 AllColumns의 하위 타입이므로 먼저 구분한다.
        if (expression.getClass() == AllTableColumns.class) {
            AllTableColumns wildcard = (AllTableColumns) expression;
            validateWildcard(wildcard);
            return ordinary(columnResolver.expandWildcard(wildcard.getTable(), scope));
        }

        if (expression.getClass() == AllColumns.class) {
            validateWildcard((AllColumns) expression);
            return ordinary(columnResolver.expandWildcard(null, scope));
        }

        if (expression.getClass() == Function.class) {
            return analyzeAggregate((Function) expression, scope);
        }

        throw badRequest("지원하지 않는 SELECT 표현식입니다: " + expression);
    }

    /** 허용한 집계 함수의 이름·옵션·인자를 검사한다. */
    private Analysis analyzeAggregate(Function function, Scope scope) {

        String name = function.getName();

        if (name == null || !AGGREGATES.contains(name.toUpperCase(Locale.ROOT))) {
            throw badRequest("지원하지 않는 함수입니다: " + name);
        }

        validateFunctionOptions(function);

        var parameters = function.getParameters();

        if (parameters == null || parameters.size() != 1) {
            throw badRequest("집계 함수에는 인자 하나를 지정하세요.");
        }

        Expression argument = parameters.get(0);

        if (argument.getClass() == AllColumns.class) {
            validateWildcard((AllColumns) argument);

            if (!"COUNT".equalsIgnoreCase(name)
                    || function.isDistinct()
                    || function.isUnique()
                    || function.isAllColumns()) {
                throw badRequest("별표 인자는 COUNT(*) 형태만 지원합니다.");
            }

            // COUNT(*)는 모든 컬럼을 출력하는 것이 아니라 행 개수를 센다.
            return new Analysis(List.of(), List.of(), true);
        }

        if (!(argument instanceof Column column)) {
            throw badRequest("집계 함수의 인자는 일반 컬럼 또는 COUNT(*)만 지원합니다.");
        }

        ResolvedColumn resolved = columnResolver.resolveColumn(column, scope);

        // 집계 내부 컬럼은 참조 대상으로 기록하지만 GROUP BY 필수 대상은 아니다.
        return new Analysis(List.of(resolved), List.of(), true);
    }

    /** 기본 집계 외의 옵션 안에 숨은 표현식을 검사 없이 허용하지 않는다. */
    private void validateFunctionOptions(Function function) {

        if (function.isEscaped()
                || function.getNamedParameters() != null
                || function.getAttribute() != null
                || function.getKeep() != null
                || function.getHavingClause() != null
                || function.getNullHandling() != null
                || function.isIgnoreNullsOutside()
                || function.getLimit() != null
                || function.getExtraKeyword() != null
                || function.getOnOverflowTruncate() != null
                || (function.getOrderByElements() != null
                    && !function.getOrderByElements().isEmpty())) {
            throw badRequest("추가 옵션이 있는 집계 함수는 아직 지원하지 않습니다.");
        }
    }

    /** 일반 별표만 지원하며 EXCEPT·REPLACE 확장 구문은 차단한다. */
    private void validateWildcard(AllColumns wildcard) {

        if ((wildcard.getExceptColumns() != null
                && !wildcard.getExceptColumns().isEmpty())
                || (wildcard.getReplaceExpressions() != null
                && !wildcard.getReplaceExpressions().isEmpty())) {
            throw badRequest("별표의 EXCEPT·REPLACE 구문은 지원하지 않습니다.");
        }
    }

    private Analysis ordinary(List<ResolvedColumn> columns) {
        return new Analysis(columns, columns, false);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * referencedColumns: 접근 정책 검사 등에 사용할 전체 참조 컬럼.
     * nonAggregateColumns: 그룹 조회에서 GROUP BY 포함 여부를 확인할 컬럼.
     * aggregate: 해당 SELECT 항목이 집계 함수인지 여부.
     */
    public record Analysis(
            List<ResolvedColumn> referencedColumns,
            List<ResolvedColumn> nonAggregateColumns,
            boolean aggregate) {

        public Analysis {
            referencedColumns = List.copyOf(referencedColumns);
            nonAggregateColumns = List.copyOf(nonAggregateColumns);
        }
    }
}