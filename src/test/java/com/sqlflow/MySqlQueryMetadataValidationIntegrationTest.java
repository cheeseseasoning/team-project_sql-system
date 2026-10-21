package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.MySqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

/** 실제 MySQL 업무 테이블의 구조를 검증한다. 업무 SQL 실행이나 데이터 변경은 하지 않는다. */
@SpringBootTest
class MySqlQueryMetadataValidationIntegrationTest {

    private static final QueryObjectName SALES = new QueryObjectName("sqlflow_demo", "demo_sales");
    private static final QueryObjectName DEPARTMENT = new QueryObjectName("sqlflow_demo", "demo_department");

    @Autowired
    private MySqlQueryMetadataValidationService service;

    @Autowired
    private MySqlMetadataMapper metadataMapper;

    @Test
    void joinAndAggregateUseActualMySqlMetadata() {
        var result = service.validate("""
                SELECT D.department_name, SUM(S.sales_amount) AS total_amount
                FROM sqlflow_demo.demo_sales S
                JOIN sqlflow_demo.demo_department D ON S.department_id = D.department_id
                WHERE S.sale_status = 'COMPLETED'
                GROUP BY D.department_name
                ORDER BY total_amount DESC
                """);

        assertTrue(result.grouped());
        assertEquals(Set.of(SALES, DEPARTMENT), result.resources());
        assertEquals(Set.of(
                new ResolvedColumn("D", DEPARTMENT, "department_name"),
                new ResolvedColumn("S", SALES, "sales_amount"),
                new ResolvedColumn("S", SALES, "department_id"),
                new ResolvedColumn("D", DEPARTMENT, "department_id"),
                new ResolvedColumn("S", SALES, "sale_status")), result.referencedColumns());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM sqlflow_demo.demo_sales S",
            "SELECT S.*, D.department_name FROM sqlflow_demo.demo_sales S "
                    + "LEFT JOIN sqlflow_demo.demo_department D ON S.department_id = D.department_id"
    })
    void wildcardUsesActualColumns(String sql) {
        var result = service.validate(sql);
        Set<String> expected = metadataMapper.selectColumns(SALES.owner(), SALES.objectName()).stream()
                .map(column -> column.getColumnName()).collect(Collectors.toSet());
        Set<String> actual = result.referencedColumns().stream()
                .filter(column -> "S".equals(column.sourceName()))
                .filter(column -> SALES.equals(column.target()))
                .map(ResolvedColumn::columnName).collect(Collectors.toSet());

        assertFalse(expected.isEmpty());
        assertEquals(expected, actual);
        assertFalse(result.grouped());
    }

    @Test
    void countStarKeepsResourceWithoutColumnReferences() {
        var result = service.validate("SELECT COUNT(*) FROM sqlflow_demo.demo_sales S");

        assertEquals(Set.of(SALES), result.resources());
        assertTrue(result.referencedColumns().isEmpty());
        assertTrue(result.grouped());
    }

    @Test
    void backticksAndColumnCaseAreHandled() {
        var result = service.validate("SELECT `S`.`SALE_ID` FROM `sqlflow_demo`.`demo_sales` `S`");

        assertEquals(Set.of(SALES), result.resources());
        assertEquals(Set.of(new ResolvedColumn("S", SALES, "sale_id")), result.referencedColumns());
    }

    @Test
    void missingColumnIsRejected() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.validate("SELECT S.missing_test_column FROM sqlflow_demo.demo_sales S"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("존재하지 않는 컬럼"));
    }

    @Test
    void missingGroupColumnIsRejected() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.validate("""
                        SELECT S.sale_id, COUNT(*)
                        FROM sqlflow_demo.demo_sales S
                        GROUP BY S.department_id
                        """));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        assertTrue(error.getReason().contains("GROUP BY에 없습니다"));
    }
}
