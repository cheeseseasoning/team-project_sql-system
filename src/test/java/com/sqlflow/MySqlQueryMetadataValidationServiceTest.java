package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
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
import com.sqlflow.execution.service.SqlConditionValidator;
import com.sqlflow.execution.service.SqlGroupingOrderValidator;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.execution.service.SqlSelectExpressionAnalyzer;
import com.sqlflow.execution.service.SqlTableExtractor;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;
import com.sqlflow.mysql.service.MySqlTableValidationService;

/**
 * 파싱·객체 확인·컬럼 연결·조건·집계 검사를 함께 검증한다.
 * MySQL 메타데이터 조회만 Mock으로 대체하며 SQL을 실행하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class MySqlQueryMetadataValidationServiceTest {

    private static final QueryObjectName USERS =
            new QueryObjectName("SQLFLOW", "USERS");

    private static final QueryObjectName DEPARTMENT =
            new QueryObjectName("SQLFLOW", "DEPARTMENT");

    @Mock
    private MySqlMetadataMapper metadataMapper;

    private MySqlQueryMetadataValidationService service;

    @BeforeEach
    void setUp() {
        MySqlColumnResolver resolver = new MySqlColumnResolver(metadataMapper);

        MySqlTableValidationService tableService =
                new MySqlTableValidationService(new SqlTableExtractor(), metadataMapper);

        SqlGroupingOrderValidator groupingValidator =
                new SqlGroupingOrderValidator(
                        resolver,
                        new SqlSelectExpressionAnalyzer(resolver));

        service = new MySqlQueryMetadataValidationService(
                new SqlParserValidator(),
                tableService,
                resolver,
                new SqlConditionValidator(resolver),
                groupingValidator);
    }

    @Test
    void joinConditionAggregateAndOrderAreValidatedTogether() {
        prepareMetadata();

        var result = service.validate(
                "SELECT D.DEPT_NAME, SUM(U.AMOUNT) AS TOTAL "
                + "FROM USERS U "
                + "JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID "
                + "WHERE U.ENABLED = 'Y' AND U.AMOUNT > 1000 "
                + "GROUP BY D.DEPT_NAME ORDER BY TOTAL DESC");

        assertTrue(result.grouped());
        assertEquals(Set.of(USERS, DEPARTMENT), result.resources());

        // SELECT뿐 아니라 ON·WHERE에서 사용한 컬럼도 반환해야 한다.
        assertEquals(Set.of(
                new ResolvedColumn("U", USERS, "DEPT_ID"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_ID"),
                new ResolvedColumn("U", USERS, "ENABLED"),
                new ResolvedColumn("U", USERS, "AMOUNT"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_NAME")
        ), result.referencedColumns());

        // ON 범위를 제한할 때 메타데이터를 다시 조회하지 않는다.
        verify(metadataMapper, times(1)).selectColumns("SQLFLOW", "USERS");
        verify(metadataMapper, times(1)).selectColumns("SQLFLOW", "DEPARTMENT");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM USERS U",
            "SELECT U.*, D.DEPT_NAME FROM USERS U "
                    + "LEFT JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID"
    })
    void wildcardQueriesAreAccepted(String sql) {
        prepareMetadata();

        var result = service.validate(sql);

        assertFalse(result.grouped());
        assertTrue(result.referencedColumns().contains(
                new ResolvedColumn("U", USERS, "USER_ID")));
        assertTrue(result.referencedColumns().contains(
                new ResolvedColumn("U", USERS, "AMOUNT")));
    }

    @Test
    void countStarKeepsResourceEvenWithoutColumnReferences() {
        prepareMetadata();

        var result = service.validate("SELECT COUNT(*) FROM USERS U");

        assertTrue(result.grouped());
        assertEquals(Set.of(USERS), result.resources());
        assertTrue(result.referencedColumns().isEmpty());
    }

    @Test
    void selfJoinLoadsColumnsOnceAndKeepsBothAliases() {
        prepareMetadata();

        var result = service.validate(
                "SELECT U.USER_ID, M.USER_ID AS MANAGER "
                + "FROM USERS U "
                + "LEFT JOIN USERS M ON U.MANAGER_ID = M.USER_ID");

        assertEquals(Set.of(USERS), result.resources());
        assertTrue(result.referencedColumns().contains(
                new ResolvedColumn("U", USERS, "USER_ID")));
        assertTrue(result.referencedColumns().contains(
                new ResolvedColumn("M", USERS, "USER_ID")));

        verify(metadataMapper, times(1)).selectColumns("SQLFLOW", "USERS");
    }

    @Test
    void onCannotReferenceAliasFromLaterJoin() {
        prepareMetadata();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(
                        "SELECT U.USER_ID FROM USERS U "
                        + "JOIN DEPARTMENT D ON U.DEPT_ID = C.COMPANY_ID "
                        + "JOIN COMPANY C ON D.COMPANY_ID = C.COMPANY_ID"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("선언되지 않은"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT U.USER_ID FROM USERS U "
                    + "JOIN DEPARTMENT D ON U.UNKNOWN_COLUMN = D.DEPT_ID",

            "SELECT U.USER_ID FROM USERS U WHERE U.UNKNOWN_COLUMN = 1",

            "SELECT U.USER_ID, SUM(U.AMOUNT) FROM USERS U "
                    + "GROUP BY U.DEPT_ID"
    })
    void invalidReferencesAndGroupingAreRejected(String sql) {
        prepareMetadata();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(sql));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT USER_ID FROM USERS UNION ALL SELECT USER_ID FROM USERS",

            "WITH T AS (SELECT USER_ID FROM USERS) SELECT USER_ID FROM T",

            "SELECT T.USER_ID FROM (SELECT USER_ID FROM USERS) T",

            "SELECT U.USER_ID FROM USERS U CROSS JOIN DEPARTMENT D",

            "SELECT U.USER_ID FROM USERS U "
                    + "JOIN DEPARTMENT D USING (DEPT_ID)",

            "SELECT USER_ID FROM USERS FOR UPDATE",

            "SELECT DISTINCT USER_ID FROM USERS",

            "SELECT DEPT_ID, COUNT(*) FROM USERS "
                    + "GROUP BY DEPT_ID HAVING COUNT(*) > 1"
    })
    void unsupportedStructuresStopBeforeMetadataLookup(String sql) {
        // 파싱 가능한 SQL인지 먼저 확인하여 문법 오류와 지원 범위를 구분한다.
        new SqlParserValidator().parseSelect(sql);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(sql));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(metadataMapper);
    }

    /** 지원 구조 검사를 통과한 테스트에서만 MySQL 응답을 준비한다. */
    private void prepareMetadata() {
        when(metadataMapper.selectCurrentDatabase()).thenReturn("SQLFLOW");

        when(metadataMapper.selectObject(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String owner = invocation.getArgument(0);
                    String name = invocation.getArgument(1);

                    if (!"SQLFLOW".equals(owner)
                            || !Set.of("USERS", "DEPARTMENT", "COMPANY").contains(name)) {
                        throw new AssertionError("예상하지 않은 객체 조회: " + owner + "." + name);
                    }

                    MySqlObjectMetadata object = new MySqlObjectMetadata();
                    object.setDatabaseName(owner);
                    object.setObjectName(name);
                    object.setObjectType("BASE TABLE");
                    return object;
                });

        when(metadataMapper.selectColumns(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String owner = invocation.getArgument(0);
                    String name = invocation.getArgument(1);

                    if (!"SQLFLOW".equals(owner)) {
                        throw new AssertionError("예상하지 않은 DB: " + owner);
                    }

                    return switch (name) {
                        case "USERS" -> columns(name, "USER_ID", "DEPT_ID", "AMOUNT", "ENABLED", "MANAGER_ID");
                        case "DEPARTMENT" -> columns(name, "DEPT_ID", "DEPT_NAME", "COMPANY_ID");
                        case "COMPANY" -> columns(name, "COMPANY_ID", "COMPANY_NAME");
                        default -> throw new AssertionError("예상하지 않은 테이블: " + name);
                    };
                });
    }

    private List<MySqlColumnMetadata> columns(String tableName, String... names) {
        List<MySqlColumnMetadata> result = new ArrayList<>();

        for (String name : names) {
            MySqlColumnMetadata column = new MySqlColumnMetadata();
            column.setDatabaseName("SQLFLOW");
            column.setTableName(tableName);
            column.setColumnName(name);
            column.setDataType(
                    name.endsWith("_NAME") || "ENABLED".equals(name)
                            ? "varchar" : "bigint");
            result.add(column);
        }

        return result;
    }
}
