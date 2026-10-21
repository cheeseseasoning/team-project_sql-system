package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

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
import com.sqlflow.execution.service.MySqlColumnResolver.Scope;
import com.sqlflow.execution.service.SqlConditionValidator;
import com.sqlflow.execution.service.SqlParserValidator;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 실제 ON·WHERE 구문으로 조건 구조와 참조 컬럼 검사를 확인한다.
 * SQL 실행이나 실제 DB 접속은 하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class SqlConditionValidatorTest {

    private static final QueryObjectName USERS =
            new QueryObjectName("SQLFLOW", "USERS");

    private static final QueryObjectName DEPARTMENT =
            new QueryObjectName("SQLFLOW", "DEPARTMENT");

    private static final String QUERY =
            "SELECT U.USER_ID FROM USERS U "
            + "JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID";

    @Mock
    private MySqlMetadataMapper metadataMapper;

    private final SqlParserValidator parser = new SqlParserValidator();

    private SqlConditionValidator validator;
    private Scope scope;

    @BeforeEach
    void setUp() {
        when(metadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(
                        column(USERS, "USER_ID"),
                        column(USERS, "DEPT_ID"),
                        column(USERS, "AMOUNT"),
                        column(USERS, "ENABLED")));

        when(metadataMapper.selectColumns("SQLFLOW", "DEPARTMENT"))
                .thenReturn(List.of(
                        column(DEPARTMENT, "DEPT_ID"),
                        column(DEPARTMENT, "DEPT_NAME")));

        MySqlColumnResolver resolver = new MySqlColumnResolver(metadataMapper);
        validator = new SqlConditionValidator(resolver);

        PlainSelect select = parse(QUERY);

        scope = resolver.createScope(
                List.of(
                        (Table) select.getFromItem(),
                        (Table) select.getJoins().get(0).getRightItem()),
                Map.of("USERS", USERS, "DEPARTMENT", DEPARTMENT));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "U.USER_ID = 1",
            "U.ENABLED <> 'N'",
            "U.AMOUNT >= 1000",
            "U.ENABLED = 'Y' AND (U.AMOUNT > 100 OR U.DEPT_ID = 10)",
            "NOT (U.ENABLED = 'N')",
            "U.DEPT_ID IS NULL",
            "U.DEPT_ID IS NOT NULL",
            "U.AMOUNT >= -100"
    })
    void supportedConditionsAreAccepted(String condition) {
        Expression expression = where(condition);

        Set<ResolvedColumn> result =
                assertDoesNotThrow(() -> validator.validate(expression, scope));

        assertFalse(result.isEmpty());
    }

    @Test
    void joinConditionCollectsColumnsFromBothSources() {
        PlainSelect select = parse(QUERY);

        // ON 구문의 컬럼은 양쪽 테이블을 모두 확인해야 한다.
        Expression on = select.getJoins().get(0)
                .getOnExpressions().iterator().next();

        Set<ResolvedColumn> result = validator.validate(on, scope);

        assertEquals(Set.of(
                new ResolvedColumn("U", USERS, "DEPT_ID"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_ID")
        ), result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "U.UNKNOWN_COLUMN = 1",
            "X.USER_ID = 1",
            "LOWER(U.ENABLED) = 'y'",
            "U.AMOUNT + 1 > 100",
            "U.DEPT_ID IN (10, 20)",
            "U.ENABLED LIKE 'Y%'",
            "U.DEPT_ID = (SELECT DEPT_ID FROM DEPARTMENT)"
    })
    void invalidOrUnsupportedConditionsAreRejected(String condition) {
        // SQL 파싱은 먼저 완료하여 검증 단계에서 차단되는지 확인한다.
        Expression expression = where(condition);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(expression, scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1 = 1 OR X.USER_ID = 1",
            "1 = 0 AND X.USER_ID = 1"
    })
    void everyLogicalBranchIsValidated(String condition) {
        Expression expression = where(condition);

        // 실제 조건의 참·거짓과 관계없이 모든 분기의 컬럼을 검사한다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validator.validate(expression, scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("선언되지 않은"));
    }

    @Test
    void absentWhereReturnsEmptyReferences() {
        Expression expression = parse(QUERY).getWhere();

        assertNull(expression);
        assertEquals(Set.of(), validator.validate(expression, scope));
    }

    /** WHERE 조건을 문자열로 직접 분해하지 않고 파싱 결과에서 가져온다. */
    private Expression where(String condition) {
        return parse(QUERY + " WHERE " + condition).getWhere();
    }

    private PlainSelect parse(String sql) {
        return assertInstanceOf(PlainSelect.class, parser.parseSelect(sql));
    }

    private MySqlColumnMetadata column(QueryObjectName target, String name) {
        MySqlColumnMetadata column = new MySqlColumnMetadata();
        column.setDatabaseName(target.owner());
        column.setTableName(target.objectName());
        column.setColumnName(name);
        column.setDataType("ENABLED".equals(name) || "DEPT_NAME".equals(name)
                ? "varchar" : "bigint");
        return column;
    }
}
