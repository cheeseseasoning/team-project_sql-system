package com.sqlflow.execution.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.MySqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.MySqlColumnResolver.Scope;
import com.sqlflow.mysql.service.MySqlTableValidationService;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.FromItem;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * 지원하는 SELECT의 구조와 MySQL 객체·컬럼·조건·그룹·정렬을 검사한다.
 * 직원별 접근 정책, 자료형 적합성, 실행 제한은 별도로 확인한다.
 */
@Service
@RequiredArgsConstructor
public class MySqlQueryMetadataValidationService {

    private final SqlParserValidator sqlParserValidator;
    private final MySqlTableValidationService tableValidationService;
    private final MySqlColumnResolver columnResolver;
    private final SqlConditionValidator conditionValidator;
    private final SqlGroupingOrderValidator groupingOrderValidator;

    /**
     * 원본 SQL을 변경하거나 실행하지 않고 검증 결과를 반환한다.
     * 메타데이터 조회에는 업무용 MySQL 연결을 사용한다.
     */
    @Transactional(transactionManager = "queryTransactionManager", readOnly = true)
    public ValidationResult validate(String sqlText) {

        Select parsed = sqlParserValidator.parseSelect(sqlText);

        if (!(parsed instanceof PlainSelect select)) {
            throw badRequest("단일 SELECT만 지원합니다. UNION·중첩 조회는 지원하지 않습니다.");
        }

        validateQueryOptions(select);

        // DB 조회 전에 FROM·JOIN의 지원 구조를 확인한다.
        List<Table> tables = collectTables(select);

        Map<String, QueryObjectName> resolvedObjects = tableValidationService.validateTables(select);
        Scope scope = columnResolver.createScope(tables, resolvedObjects);

        Set<ResolvedColumn> references = new LinkedHashSet<>();

        // ON에서는 현재 JOIN까지 등장한 대상만 참조할 수 있다.
        validateJoinConditions(select, tables, scope, references);

        // WHERE에서는 전체 FROM·JOIN 대상을 참조할 수 있다.
        references.addAll(conditionValidator.validate(select.getWhere(), scope));

        // SELECT·GROUP BY·ORDER BY의 컬럼 참조와 집계 규칙을 검사한다.
        var grouping = groupingOrderValidator.validate(select, scope);
        references.addAll(grouping.referencedColumns());

        // COUNT(*)처럼 참조 컬럼이 없어도 정책 검사 대상은 유지한다.
        Set<QueryObjectName> resources = new LinkedHashSet<>();

        for (Table table : tables) {
            QueryObjectName target = resolvedObjects.get(table.getFullyQualifiedName());

            if (target == null) {
                throw badRequest("실제 조회 대상 정보를 확인할 수 없습니다.");
            }

            resources.add(target);
        }

        return new ValidationResult(resources, references, grouping.grouped());
    }

    /** FROM과 JOIN의 직접 참조 대상을 SQL에 등장한 순서대로 수집한다. */
    private List<Table> collectTables(PlainSelect select) {

        List<Table> tables = new ArrayList<>();
        tables.add(requireTable(select.getFromItem()));

        if (select.getJoins() != null) {
            for (Join join : select.getJoins()) {
                validateJoinShape(join);
                tables.add(requireTable(join.getRightItem()));
            }
        }

        return List.copyOf(tables);
    }

    /** INNER JOIN·LEFT JOIN의 명시적인 ON 방식만 지원한다. */
    private void validateJoinShape(Join join) {

        if (join.isSimple()
                || join.isRight()
                || join.isFull()
                || join.isCross()
                || join.isNatural()
                || join.isStraight()
                || join.isApply()
                || join.isSemi()
                || join.isGlobal()
                || join.isWindowJoin()
                || join.getJoinHint() != null
                || (join.isOuter() && !join.isLeft())) {
            throw badRequest("JOIN은 INNER JOIN·LEFT JOIN의 ON 방식만 지원합니다.");
        }

        if (join.getUsingColumns() != null && !join.getUsingColumns().isEmpty()) {
            throw badRequest("JOIN USING은 지원하지 않습니다. ON 조건을 사용하세요.");
        }

        if (join.getOnExpressions() == null
                || join.getOnExpressions().size() != 1
                || join.getOnExpressions().iterator().next() == null) {
            throw badRequest("JOIN마다 ON 조건 하나를 지정하세요.");
        }
    }

    /** 별도 해석이 필요한 서브쿼리·PIVOT 등의 FROM 구조는 차단한다. */
    private Table requireTable(FromItem item) {

        if (!(item instanceof Table table)) {
            throw badRequest("FROM·JOIN에는 직접 테이블 또는 뷰를 지정하세요.");
        }

        if (table.getPivot() != null
                || table.getUnPivot() != null
                || table.getSampleClause() != null
                || table.getIndexHint() != null
                || table.getSqlServerHints() != null) {
            throw badRequest("조회 대상의 확장 옵션은 지원하지 않습니다.");
        }

        // 대상 이름 형식과 실제 존재 여부는 MySQL 객체 검사에서 확인한다.
        return table;
    }

    /** 기존 메타데이터를 재사용하면서 각 ON의 참조 범위만 제한한다. */
    private void validateJoinConditions(
            PlainSelect select,
            List<Table> tables,
            Scope scope,
            Set<ResolvedColumn> references) {

        if (select.getJoins() == null) {
            return;
        }

        for (int index = 0; index < select.getJoins().size(); index++) {
            Join join = select.getJoins().get(index);

            // FROM 한 개와 현재 JOIN까지의 대상만 포함한다.
            Scope visibleScope = columnResolver.restrictScope(scope, tables.subList(0, index + 2));

            var condition = join.getOnExpressions().iterator().next();
            references.addAll(conditionValidator.validate(condition, visibleScope));
        }
    }

    /** 지원하지 않는 SELECT 확장 절은 검사 없이 통과시키지 않는다. */
    private void validateQueryOptions(PlainSelect select) {

        if (select.getWithItemsList() != null && !select.getWithItemsList().isEmpty()) {
            throw badRequest("WITH 구문은 지원하지 않습니다.");
        }

        if (select.getDistinct() != null || select.getHaving() != null) {
            throw badRequest("SELECT DISTINCT·HAVING은 현재 지원하지 않습니다.");
        }

        if (select.getIntoTempTable() != null
                || (select.getIntoTables() != null && !select.getIntoTables().isEmpty())
                || select.getForMode() != null
                || select.getForClause() != null
                || select.getForUpdateTable() != null
                || select.getWait() != null
                || select.isNoWait()
                || select.isSkipLocked()) {
            throw badRequest("결과 저장 SQL과 잠금 조회 구문은 허용하지 않습니다.");
        }

        if (select.getOracleHierarchical() != null
                || select.getQualify() != null
                || select.getPivot() != null
                || select.getUnPivot() != null
                || select.getOracleHint() != null
                || select.isOracleSiblings()
                || (select.getWindowDefinitions() != null && !select.getWindowDefinitions().isEmpty())
                || (select.getLateralViews() != null && !select.getLateralViews().isEmpty())) {
            throw badRequest("계층 조회·윈도 정의 등 확장 조회 구문은 지원하지 않습니다.");
        }

        // 결과 행 수 제한은 실행 단계의 시스템 정책에서 적용한다.
        if (select.getLimit() != null
                || select.getLimitBy() != null
                || select.getOffset() != null
                || select.getFetch() != null
                || select.getTop() != null
                || select.getFirst() != null
                || select.getSkip() != null) {
            throw badRequest("사용자 지정 페이징·행 제한 구문은 현재 지원하지 않습니다.");
        }

        if (select.getIsolation() != null
                || select.getSampleClause() != null
                || select.getOptimizeFor() != null
                || select.getPreferringClause() != null
                || select.getForXmlPath() != null
                || select.getKsqlWindow() != null
                || select.getBigQuerySelectQualifier() != null
                || select.isUsingFinal()
                || select.isUsingOnly()
                || select.isUseWithNoLog()
                || select.isEmitChanges()
                || select.getMySqlHintStraightJoin()
                || select.getMySqlSqlCalcFoundRows()
                || select.getMySqlSqlCacheFlag() != null) {
            throw badRequest("현재 지원 범위를 벗어난 SELECT 옵션입니다.");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * resources: 정책 검사에 사용할 실제 조회 대상.
     * referencedColumns: SELECT·ON·WHERE·GROUP BY·ORDER BY의 실제 참조 컬럼.
     * grouped: 그룹 또는 집계 조회 여부.
     */
    public record ValidationResult(
            Set<QueryObjectName> resources,
            Set<ResolvedColumn> referencedColumns,
            boolean grouped) {

        public ValidationResult {
            resources = Set.copyOf(resources);
            referencedColumns = Set.copyOf(referencedColumns);
        }
    }
}