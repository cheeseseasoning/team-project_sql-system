package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.*;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;
import com.sqlflow.mysql.service.MySqlTableValidationService;
import com.sqlflow.request.service.DmlSqlValidationService;

/** 실제 파서·객체 추출·검증 서비스를 사용한다. DB 응답은 대역이며 실제 EXPLAIN 검증과 구분한다. */
class SqlValidationSyntaxMatrixTest {
    private MySqlQueryMetadataValidationService select;
    private DmlSqlValidationService dml;
    private DataAccessValidationService access;

    @BeforeEach void setUp() {
        var mapper = mock(MySqlMetadataMapper.class);
        var explain = mock(SqlExplainValidationService.class);
        access = mock(DataAccessValidationService.class);
        when(explain.validate(anyString(), anyString())).thenReturn(
                new SqlExplainValidationService.Result(SqlExplainValidationService.State.VALID, "DB 응답 대역"));
        when(mapper.selectCurrentDatabase()).thenReturn("demo");
        when(mapper.selectObject(anyString(), anyString())).thenAnswer(call -> {
            String name = call.getArgument(1);
            if (!Set.of("users", "department").contains(name)) return null;
            var object = new MySqlObjectMetadata();
            object.setDatabaseName(call.getArgument(0));
            object.setObjectName(name);
            object.setObjectType("BASE TABLE");
            return object;
        });
        when(access.validate(anyLong(), anySet())).thenReturn(new DataAccessValidationService.ValidationResult("GENERAL", Set.of(1L)));
        var tables = new MySqlTableValidationService(new SqlTableExtractor(), mapper);
        var basic = new SqlBasicValidator();
        select = new MySqlQueryMetadataValidationService(new SqlParserValidator(), tables, basic, explain);
        dml = new DmlSqlValidationService(basic, explain, tables, access);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT u.id FROM users u INNER JOIN department d ON u.dept_id=d.id;",
        "SELECT u.id FROM users u LEFT JOIN department d ON u.dept_id=d.id;",
        "SELECT u.id FROM users u RIGHT JOIN department d ON u.dept_id=d.id;",
        "SELECT u.id FROM users u CROSS JOIN department d;",
        "SELECT id FROM users WHERE dept_id IN (SELECT id FROM department);",
        "SELECT u.id FROM users u WHERE EXISTS (SELECT 1 FROM department d WHERE d.id=u.dept_id);",
        "SELECT id FROM (SELECT id FROM users) sub;",
        "WITH c AS (SELECT id FROM users) SELECT id FROM c;",
        "SELECT id FROM users UNION ALL SELECT id FROM department;",
        "SELECT DISTINCT id FROM users ORDER BY id LIMIT 10 OFFSET 1;",
        "SELECT dept_id, SUM(amount) FROM users GROUP BY dept_id HAVING SUM(amount)>0 ORDER BY dept_id;",
        "SELECT id, ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY id) AS rn FROM users;"
    })
    void supportedSelectFormsKeepEveryReferenceTarget(String sql) {
        var actual = select.validate(sql).resources();
        var expected = new java.util.HashSet<QueryObjectName>();
        expected.add(new QueryObjectName("demo", "users"));
        if (sql.contains("department")) expected.add(new QueryObjectName("demo", "department"));
        assertEquals(expected, actual);
        System.out.println("SYNTAX_MATRIX SELECT VALID_WITH_DB_STUB | " + sql);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "INSERT INTO users(id) VALUES(1);",
        "INSERT INTO users(id) VALUES(1),(2);",
        "INSERT INTO users(id) SELECT id FROM department;",
        "UPDATE users SET amount=1 WHERE id=1;",
        "DELETE FROM users WHERE id=1;",
        "UPDATE users u JOIN department d ON u.dept_id=d.id SET u.amount=1 WHERE u.id=1;",
        "DELETE u FROM users u JOIN department d ON u.dept_id=d.id WHERE u.id=1;"
    })
    void dmlFormsRemainSubmittableWithReviewWhenExtractionIsUnsupported(String sql) {
        String type = sql.substring(0, sql.indexOf(' '));
        var result = dml.validateForReview(1L, DmlSqlValidationService.SqlType.valueOf(type), sql);
        assertNotEquals(DmlSqlValidationService.Status.BLOCKED, result.status(), result.message());
        if (result.status() == DmlSqlValidationService.Status.VALID) {
            var expected = new java.util.HashSet<QueryObjectName>();
            expected.add(new QueryObjectName("demo", "users"));
            if (sql.contains("department")) expected.add(new QueryObjectName("demo", "department"));
            verify(access).validate(eq(1L), eq(expected));
        }
        System.out.println("SYNTAX_MATRIX " + type + " " + result.status() + "_WITH_DB_STUB | " + sql);
    }

    @Test void updateAndDeleteWithoutWhereHaveExplicitWarnings() {
        for (String sql : java.util.List.of("UPDATE users SET amount=1;", "DELETE FROM users;")) {
            var type = DmlSqlValidationService.SqlType.valueOf(sql.substring(0, sql.indexOf(' ')));
            var result = dml.validateForReview(1L, type, sql);
            assertEquals(DmlSqlValidationService.Status.WARNING, result.status());
            assertTrue(result.message().contains("WHERE"));
            System.out.println("SYNTAX_MATRIX " + type + " WARNING_NO_WHERE | " + sql);
        }
    }
}
