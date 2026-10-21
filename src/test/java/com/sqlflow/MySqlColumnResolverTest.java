package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 여러 테이블의 컬럼 연결과 별표 확장을 검증한다.
 * SQL은 실제 파싱하고, MySQL 컬럼 조회만 Mock으로 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class MySqlColumnResolverTest {

    private static final QueryObjectName USERS =
            new QueryObjectName("SQLFLOW", "USERS");

    private static final QueryObjectName DEPARTMENT =
            new QueryObjectName("SQLFLOW", "DEPARTMENT");

    @Mock
    private MySqlMetadataMapper metadataMapper;

    private MySqlColumnResolver resolver;

    private final SqlParserValidator parser = new SqlParserValidator();

    @BeforeEach
    void setUp() {
        resolver = new MySqlColumnResolver(metadataMapper);
    }

    @Test
    void qualifiedColumnsAreConnectedToDifferentTargets() {
        Scope scope = joinScope();

        ResolvedColumn user = resolver.resolveColumn(
                new Column(new Table("U"), "USER_NAME"), scope);

        ResolvedColumn department = resolver.resolveColumn(
                new Column(new Table("D"), "DEPT_NAME"), scope);

        assertEquals(
                new ResolvedColumn("U", USERS, "USER_NAME"), user);
        assertEquals(
                new ResolvedColumn("D", DEPARTMENT, "DEPT_NAME"), department);
    }

    @Test
    void unqualifiedColumnIsAcceptedWhenOnlyOneSourceHasIt() {
        Scope scope = joinScope();

        ResolvedColumn result = resolver.resolveColumn(
                new Column("USER_NAME"), scope);

        assertEquals(new ResolvedColumn("U", USERS, "USER_NAME"), result);
    }

    @Test
    void ambiguousUnqualifiedColumnIsRejected() {
        Scope scope = joinScope();

        // DEPT_ID는 USERS와 DEPARTMENT 양쪽에 있으므로 접두어가 필요하다.
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveColumn(new Column("DEPT_ID"), scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("여러 테이블"));
    }

    @Test
    void unknownAliasIsRejected() {
        Scope scope = joinScope();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveColumn(
                        new Column(new Table("X"), "USER_NAME"), scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("선언되지 않은"));
    }

    @Test
    void missingColumnIsRejectedEvenWhenAliasExists() {
        Scope scope = joinScope();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resolver.resolveColumn(
                        new Column(new Table("U"), "UNKNOWN_COLUMN"), scope));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("존재하지 않는 컬럼"));
    }

    @Test
    void wildcardExpandsAllSourcesWithoutMergingDuplicateNames() {
        Scope scope = joinScope();

        List<ResolvedColumn> result = resolver.expandWildcard(null, scope);

        // FROM·JOIN 순서와 각 테이블의 컬럼 순서를 유지한다.
        assertEquals(List.of(
                new ResolvedColumn("U", USERS, "USER_ID"),
                new ResolvedColumn("U", USERS, "USER_NAME"),
                new ResolvedColumn("U", USERS, "DEPT_ID"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_ID"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_NAME")
        ), result);
    }

    @Test
    void qualifiedWildcardExpandsOnlyItsSource() {
        Scope scope = joinScope();

        List<ResolvedColumn> result =
                resolver.expandWildcard(new Table("D"), scope);

        assertEquals(List.of(
                new ResolvedColumn("D", DEPARTMENT, "DEPT_ID"),
                new ResolvedColumn("D", DEPARTMENT, "DEPT_NAME")
        ), result);
    }

    @Test
    void selfJoinKeepsAliasesSeparateAndLoadsMetadataOnce() {
        prepareColumns(USERS, "USER_ID", "MANAGER_ID");

        Scope scope = scope(
                "SELECT U.USER_ID FROM USERS U "
                + "JOIN USERS M ON U.MANAGER_ID = M.USER_ID",
                Map.of("USERS", USERS));

        ResolvedColumn user = resolver.resolveColumn(
                new Column(new Table("U"), "USER_ID"), scope);

        ResolvedColumn manager = resolver.resolveColumn(
                new Column(new Table("M"), "USER_ID"), scope);

        // 실제 대상이 같아도 SQL의 참조 별칭은 구분한다.
        assertNotEquals(user, manager);
        assertEquals(USERS, user.target());
        assertEquals(USERS, manager.target());

        verify(metadataMapper, times(1)).selectColumns("SQLFLOW", "USERS");
    }

    @Test
    void duplicateAliasesAreRejected() {
        prepareColumns(USERS, "USER_ID", "DEPT_ID");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> scope(
                        "SELECT U.USER_ID FROM USERS U "
                        + "JOIN DEPARTMENT U ON U.DEPT_ID = U.DEPT_ID",
                        Map.of("USERS", USERS, "DEPARTMENT", DEPARTMENT)));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("중복"));

        // 중복 별칭은 두 번째 테이블의 컬럼을 조회하기 전에 차단한다.
        verify(metadataMapper, never()).selectColumns("SQLFLOW", "DEPARTMENT");
    }

    @Test
    void backtickColumnUsesCaseInsensitiveLookup() {
        prepareColumns(USERS, "UserName");
        Scope scope = scope("SELECT `u`.`username` FROM USERS `u`", Map.of("USERS", USERS));

        ResolvedColumn result = resolver.resolveColumn(new Column(new Table("`u`"), "`USERNAME`"), scope);

        // 비교할 때는 대소문자를 무시하지만 실제 컬럼명은 유지한다.
        assertEquals(new ResolvedColumn("u", USERS, "UserName"), result);

        // Mock의 기본 caseMode는 0이므로 테이블 별칭의 대소문자는 구분한다.
        assertThrows(ResponseStatusException.class,
                () -> resolver.resolveColumn(new Column(new Table("`U`"), "`username`"), scope));
    }

    @Test
    void caseInsensitiveServerMatchesTableAliasCase() {
        when(metadataMapper.selectLowerCaseTableNames()).thenReturn(1);
        prepareColumns(USERS, "UserName");
        Scope scope = scope("SELECT u.username FROM USERS u", Map.of("USERS", USERS));

        ResolvedColumn result = resolver.resolveColumn(new Column(new Table("U"), "USERNAME"), scope);

        assertEquals(new ResolvedColumn("U", USERS, "UserName"), result);
    }

    @Test
    void resolvedTargetIsUsedForMetadataLookup() {
        QueryObjectName employees =
                new QueryObjectName("HR", "EMPLOYEES");

        prepareColumns(employees, "EMPLOYEE_ID");

        // 앞 단계에서 확인한 실제 대상으로 컬럼을 조회한다.
        Scope scope = scope(
                "SELECT S.EMPLOYEE_ID FROM STAFF S",
                Map.of("STAFF", employees));

        ResolvedColumn result = resolver.resolveColumn(
                new Column(new Table("S"), "EMPLOYEE_ID"), scope);

        assertEquals(
                new ResolvedColumn("S", employees, "EMPLOYEE_ID"), result);

        verify(metadataMapper).selectColumns("HR", "EMPLOYEES");
        verify(metadataMapper, never()).selectColumns("SQLFLOW", "STAFF");
    }

    /** 공통 JOIN 대상과 컬럼 정보를 준비한다. */
    private Scope joinScope() {
        prepareColumns(USERS, "USER_ID", "USER_NAME", "DEPT_ID");
        prepareColumns(DEPARTMENT, "DEPT_ID", "DEPT_NAME");

        return scope(
                "SELECT U.USER_NAME FROM USERS U "
                + "JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID",
                Map.of("USERS", USERS, "DEPARTMENT", DEPARTMENT));
    }

    /** 실제 SQL에서 FROM·JOIN 테이블을 꺼내 검사 범위를 만든다. */
    private Scope scope(String sql,
            Map<String, QueryObjectName> resolvedObjects) {

        PlainSelect select = assertInstanceOf(
                PlainSelect.class, parser.parseSelect(sql));

        List<Table> tables = new ArrayList<>();
        tables.add(assertInstanceOf(Table.class, select.getFromItem()));

        if (select.getJoins() != null) {
            for (var join : select.getJoins()) {
                tables.add(assertInstanceOf(Table.class, join.getRightItem()));
            }
        }

        return resolver.createScope(tables, resolvedObjects);
    }

    /** MySQL에서 반환할 컬럼 이름과 순서를 구성한다. */
    private void prepareColumns(QueryObjectName target, String... names) {
        List<MySqlColumnMetadata> columns = List.of(names).stream()
                .map(name -> {
                    MySqlColumnMetadata column = new MySqlColumnMetadata();
                    column.setDatabaseName(target.owner());
                    column.setTableName(target.objectName());
                    column.setColumnName(name);
                    column.setDataType("varchar");
                    return column;
                })
                .collect(Collectors.toList());

        when(metadataMapper.selectColumns(target.owner(), target.objectName()))
                .thenReturn(columns);
    }
}
