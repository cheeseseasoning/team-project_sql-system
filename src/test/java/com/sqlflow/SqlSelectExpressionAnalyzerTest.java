package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.mysql.domain.MySqlColumnMetadata;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;
import com.sqlflow.execution.service.MySqlColumnResolver;
import com.sqlflow.execution.service.MySqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.MySqlColumnResolver.Scope;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlSelectExpressionAnalyzer;
import com.sqlflow.execution.service.SqlSelectExpressionAnalyzer.Analysis;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

/** 실제 SELECT 표현식과 Mock 메타데이터로 분석 규칙을 검증한다. */
@ExtendWith(MockitoExtension.class)
class SqlSelectExpressionAnalyzerTest {

    private static final QueryObjectName SALES =
            new QueryObjectName("SQLFLOW", "SALES");

    @Mock
    private MySqlMetadataMapper metadataMapper;

    private final SqlParserValidator parser = new SqlParserValidator();

    private SqlSelectExpressionAnalyzer analyzer;
    private Scope scope;

    @BeforeEach
    void setUp() {
        when(metadataMapper.selectColumns("SQLFLOW", "SALES"))
                .thenReturn(List.of(column("DEPT_ID"), column("AMOUNT")));

        MySqlColumnResolver resolver = new MySqlColumnResolver(metadataMapper);
        analyzer = new SqlSelectExpressionAnalyzer(resolver);

        PlainSelect select = (PlainSelect) parser.parseSelect(
                "SELECT * FROM SALES S");

        scope = resolver.createScope(
                List.of((Table) select.getFromItem()),
                Map.of("SALES", SALES));
    }

    @Test
    void ordinaryColumnRequiresGroupByCheck() {
        Analysis result = analyze("S.DEPT_ID");

        List<ResolvedColumn> expected =
                List.of(new ResolvedColumn("S", SALES, "DEPT_ID"));

        assertEquals(expected, result.referencedColumns());
        assertEquals(expected, result.nonAggregateColumns());
        assertFalse(result.aggregate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COUNT", "SUM", "AVG", "MIN", "MAX"})
    void basicAggregatesResolveTheirColumn(String function) {
        Analysis result = analyze(function + "(S.AMOUNT)");

        assertEquals(
                List.of(new ResolvedColumn("S", SALES, "AMOUNT")),
                result.referencedColumns());
        assertTrue(result.nonAggregateColumns().isEmpty());
        assertTrue(result.aggregate());
    }

    @Test
    void countStarDoesNotExpandOutputColumns() {
        Analysis result = analyze("COUNT(*)");

        assertTrue(result.referencedColumns().isEmpty());
        assertTrue(result.nonAggregateColumns().isEmpty());
        assertTrue(result.aggregate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "S.*"})
    void wildcardExpandsColumnsForGroupByCheck(String expression) {
        Analysis result = analyze(expression);

        List<ResolvedColumn> expected = List.of(
                new ResolvedColumn("S", SALES, "DEPT_ID"),
                new ResolvedColumn("S", SALES, "AMOUNT"));

        assertEquals(expected, result.referencedColumns());
        assertEquals(expected, result.nonAggregateColumns());
        assertFalse(result.aggregate());
    }

    @Test
    void distinctAggregateStillChecksItsColumn() {
        Analysis result = analyze("COUNT(DISTINCT S.DEPT_ID)");

        assertEquals(
                List.of(new ResolvedColumn("S", SALES, "DEPT_ID")),
                result.referencedColumns());
        assertTrue(result.aggregate());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SUM(S.UNKNOWN_COLUMN)",
            "LOWER(S.AMOUNT)",
            "S.AMOUNT + 1",
            "SUM(S.AMOUNT + 1)",
            "SUM(MAX(S.AMOUNT))",
            "SUM(*)",
            "COUNT(S.*)"
    })
    void unsupportedOrInvalidExpressionsAreRejected(String expression) {
        // 파싱 오류가 아닌 분석 단계의 차단인지 확인한다.
        PlainSelect select = parse(expression);
        var parsedExpression = select.getSelectItem(0).getExpression();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> analyzer.analyze(parsedExpression, scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private Analysis analyze(String expression) {
        return analyzer.analyze(
                parse(expression).getSelectItem(0).getExpression(), scope);
    }

    private PlainSelect parse(String expression) {
        return assertInstanceOf(
                PlainSelect.class,
                parser.parseSelect("SELECT " + expression + " FROM SALES S"));
    }

    private MySqlColumnMetadata column(String name) {
        MySqlColumnMetadata column = new MySqlColumnMetadata();
        column.setDatabaseName("SQLFLOW");
        column.setTableName("SALES");
        column.setColumnName(name);
        column.setDataType("bigint");
        return column;
    }
}
