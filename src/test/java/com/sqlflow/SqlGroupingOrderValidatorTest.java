package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import com.sqlflow.execution.service.SqlGroupingOrderValidator;
import com.sqlflow.execution.service.SqlGroupingOrderValidator.Result;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlSelectExpressionAnalyzer;

import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * SELECT·GROUP BY·ORDER BY의 관계를 실제 SQL 파싱 결과로 검증한다.
 * ON·WHERE 조건과 실제 DB 실행은 이 테스트 범위에서 제외한다.
 */
@ExtendWith(MockitoExtension.class)
class SqlGroupingOrderValidatorTest {

    private static final QueryObjectName USERS =
            new QueryObjectName("SQLFLOW", "USERS");

    private static final QueryObjectName DEPARTMENT =
            new QueryObjectName("SQLFLOW", "DEPARTMENT");

    @Mock
    private MySqlMetadataMapper metadataMapper;

    private final SqlParserValidator parser = new SqlParserValidator();

    private MySqlColumnResolver resolver;
    private SqlGroupingOrderValidator validator;

    @BeforeEach
    void setUp() {
        // 쿼리에 실제로 등장하는 대상만 조회되도록 공통 응답을 구성한다.
        when(metadataMapper.selectColumns(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String owner = invocation.getArgument(0);
                    String name = invocation.getArgument(1);

                    if (!"SQLFLOW".equals(owner)) {
                        throw new AssertionError("예상하지 않은 스키마: " + owner);
                    }

                    return switch (name) {
                        case "USERS" -> columns(
                                USERS, "USER_ID", "DEPT_ID", "AMOUNT");
                        case "DEPARTMENT" -> columns(
                                DEPARTMENT, "DEPT_ID", "DEPT_NAME");
                        default -> throw new AssertionError(
                                "예상하지 않은 테이블: " + name);
                    };
                });

        resolver = new MySqlColumnResolver(metadataMapper);

        validator = new SqlGroupingOrderValidator(
                resolver, new SqlSelectExpressionAnalyzer(resolver));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT U.USER_ID FROM USERS U ORDER BY U.USER_ID DESC",
            "SELECT * FROM USERS U",
            "SELECT U.* FROM USERS U",
            "SELECT COUNT(*) FROM USERS U",
            "SELECT U.DEPT_ID, SUM(U.AMOUNT) AS TOTAL FROM USERS U "
                    + "GROUP BY U.DEPT_ID ORDER BY TOTAL DESC"
    })
    void supportedQueriesAreAccepted(String sql) {
        assertDoesNotThrow(() -> validate(sql));
    }

    @Test
    void joinAggregateUsesResolvedColumnsAndOutputAlias() {
        Result result = validate(
                "SELECT D.DEPT_NAME, SUM(U.AMOUNT) AS TOTAL "
                + "FROM USERS U "
                + "JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID "
                + "GROUP BY D.DEPT_NAME ORDER BY TOTAL DESC");

        assertTrue(result.grouped());
        assertEquals(2, result.selectItems().size());

        // 이번 검사 대상인 SELECT·GROUP BY·ORDER BY의 참조 컬럼을 확인한다.
        assertEquals(Set.of(
                new ResolvedColumn("D", DEPARTMENT, "DEPT_NAME"),
                new ResolvedColumn("U", USERS, "AMOUNT")
        ), result.referencedColumns());
    }

    @Test
    void wildcardIsAcceptedWhenAllExpandedColumnsAreGrouped() {
        Result result = validate(
                "SELECT U.* FROM USERS U "
                + "GROUP BY U.USER_ID, U.DEPT_ID, U.AMOUNT");

        assertTrue(result.grouped());
        assertEquals(3, result.selectItems().get(0)
                .nonAggregateColumns().size());
    }

    @Test
    void aggregateInOrderByIsIncludedInAnalysis() {
        Result result = validate(
                "SELECT U.DEPT_ID FROM USERS U "
                + "GROUP BY U.DEPT_ID ORDER BY SUM(U.AMOUNT) DESC");

        assertTrue(result.grouped());
        assertTrue(result.referencedColumns().contains(
                new ResolvedColumn("U", USERS, "AMOUNT")));
    }

    @Test
    void backtickOrderAliasIsCaseInsensitive() {
        assertDoesNotThrow(() -> validate(
                "SELECT SUM(U.AMOUNT) AS `Total` FROM USERS U ORDER BY `Total` DESC"));
        assertDoesNotThrow(() -> validate(
                "SELECT SUM(U.AMOUNT) AS `Total` FROM USERS U ORDER BY `TOTAL` DESC"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // SELECT의 일반 컬럼이 GROUP BY에 없음
            "SELECT U.USER_ID, SUM(U.AMOUNT) FROM USERS U "
                    + "GROUP BY U.DEPT_ID",

            // ORDER BY의 일반 컬럼이 GROUP BY에 없음
            "SELECT U.DEPT_ID, SUM(U.AMOUNT) FROM USERS U "
                    + "GROUP BY U.DEPT_ID ORDER BY U.USER_ID",

            // 집계와 일반 컬럼을 섞었지만 GROUP BY가 없음
            "SELECT U.USER_ID, COUNT(*) FROM USERS U",

            // 정렬에 사용한 출력 별칭이 중복됨
            "SELECT U.USER_ID AS X, U.DEPT_ID AS X "
                    + "FROM USERS U ORDER BY X",

            // 실제 컬럼도 출력 별칭도 아닌 정렬 이름
            "SELECT U.USER_ID FROM USERS U ORDER BY UNKNOWN_NAME",

            // 현재 지원하지 않는 순번 정렬
            "SELECT U.USER_ID FROM USERS U ORDER BY 1",

            // 현재 지원하지 않는 그룹 표현식
            "SELECT SUM(U.AMOUNT) FROM USERS U GROUP BY U.DEPT_ID + 1",

            // 후속 지원 범위
            "SELECT U.DEPT_ID, COUNT(*) FROM USERS U "
                    + "GROUP BY U.DEPT_ID HAVING COUNT(*) > 1",

            "SELECT DISTINCT U.DEPT_ID FROM USERS U"
    })
    void invalidOrUnsupportedQueriesAreRejected(String sql) {
        // 파싱과 검사 범위 구성은 먼저 수행하여 검증 단계의 오류만 확인한다.
        PlainSelect select = parse(sql);
        var scope = createScope(select);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(select, scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private Result validate(String sql) {
        PlainSelect select = parse(sql);
        return validator.validate(select, createScope(select));
    }

    /** SQL마다 실제 FROM·JOIN 대상만 검사 범위에 등록한다. */
    private MySqlColumnResolver.Scope createScope(PlainSelect select) {
        List<Table> tables = new ArrayList<>();
        tables.add(assertInstanceOf(Table.class, select.getFromItem()));

        if (select.getJoins() != null) {
            for (var join : select.getJoins()) {
                tables.add(assertInstanceOf(Table.class, join.getRightItem()));
            }
        }

        return resolver.createScope(
                tables,
                Map.of("USERS", USERS, "DEPARTMENT", DEPARTMENT));
    }

    private PlainSelect parse(String sql) {
        return assertInstanceOf(PlainSelect.class, parser.parseSelect(sql));
    }

    private List<MySqlColumnMetadata> columns(
            QueryObjectName target, String... names) {

        List<MySqlColumnMetadata> result = new ArrayList<>();

        for (String name : names) {
            MySqlColumnMetadata column = new MySqlColumnMetadata();
            column.setDatabaseName(target.owner());
            column.setTableName(target.objectName());
            column.setColumnName(name);
            column.setDataType("DEPT_NAME".equals(name) ? "varchar" : "bigint");
            result.add(column);
        }

        return result;
    }
}
