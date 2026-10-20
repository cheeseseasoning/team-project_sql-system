package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.mapper.OracleMetadataMapper;
import com.sqlflow.execution.service.SqlColumnResolver.ResolvedColumn;
import com.sqlflow.execution.service.SqlQueryMetadataValidationService;

/**
 * 실제 Oracle 메타데이터와 전체 검증 Service의 연결을 확인한다.
 * 업무 SQL 실행, 테이블 생성, 업무 데이터 변경은 하지 않는다.
 */
@SpringBootTest
class SqlQueryMetadataValidationIntegrationTest {

    @Autowired
    private SqlQueryMetadataValidationService service;

    @Autowired
    private OracleMetadataMapper metadataMapper;

    private OracleObjectName users;
    private OracleObjectName department;

    @BeforeEach
    void setUp() {
        String schema = metadataMapper.selectCurrentSchema();

        assertNotNull(schema);
        assertFalse(schema.isBlank());

        users = new OracleObjectName(schema, "USERS");
        department = new OracleObjectName(schema, "DEPARTMENT");
    }

    @Test
    void joinAndAggregateAreValidatedAgainstActualMetadata() {
        var result = service.validate(
                "SELECT D.DEPT_NAME, COUNT(U.USER_ID) AS USER_COUNT "
                + "FROM USERS U "
                + "JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID "
                + "WHERE U.ENABLED = 'Y' "
                + "GROUP BY D.DEPT_NAME "
                + "ORDER BY USER_COUNT DESC");

        assertTrue(result.grouped());
        assertEquals(Set.of(users, department), result.resources());

        assertEquals(Set.of(
                new ResolvedColumn("D", department, "DEPT_NAME"),
                new ResolvedColumn("U", users, "USER_ID"),
                new ResolvedColumn("U", users, "DEPT_ID"),
                new ResolvedColumn("D", department, "DEPT_ID"),
                new ResolvedColumn("U", users, "ENABLED")
        ), result.referencedColumns());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM USERS U",
            "SELECT U.*, D.DEPT_NAME FROM USERS U "
                    + "LEFT JOIN DEPARTMENT D ON U.DEPT_ID = D.DEPT_ID"
    })
    void wildcardUsesActualColumnMetadata(String sql) {
        var result = service.validate(sql);

        Set<String> expectedColumns = metadataMapper
                .selectColumns(users.owner(), users.objectName())
                .stream()
                .map(column -> column.getColumnName())
                .collect(java.util.stream.Collectors.toSet());

        assertFalse(expectedColumns.isEmpty());

        Set<String> actualColumns = result.referencedColumns().stream()
                .filter(column -> "U".equals(column.sourceName()))
                .filter(column -> users.equals(column.target()))
                .map(ResolvedColumn::columnName)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(expectedColumns, actualColumns);
        assertFalse(result.grouped());
    }

    @Test
    void countStarKeepsActualResource() {
        var result = service.validate("SELECT COUNT(*) FROM USERS U");

        assertEquals(Set.of(users), result.resources());
        assertTrue(result.referencedColumns().isEmpty());
        assertTrue(result.grouped());
    }

    @Test
    void publicDualSynonymResolvesToActualTarget() {
        // 기존 OracleSynonymMapperTest와 같은 PUBLIC.DUAL 구성을 전제로 한다.
        var result = service.validate("SELECT D.DUMMY FROM DUAL D");

        OracleObjectName dual = new OracleObjectName("SYS", "DUAL");

        assertEquals(Set.of(dual), result.resources());
        assertEquals(
                Set.of(new ResolvedColumn("D", dual, "DUMMY")),
                result.referencedColumns());
    }

    @Test
    void missingColumnIsRejectedUsingActualMetadata() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(
                        "SELECT U.SQLFLOW_TEST_MISSING_COLUMN FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("존재하지 않는 컬럼"));
    }

    @Test
    void missingGroupColumnIsRejectedAfterMetadataLookup() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(
                        "SELECT U.USER_NAME, COUNT(*) FROM USERS U "
                        + "GROUP BY U.DEPT_ID"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("GROUP BY에 없습니다"));
    }
}