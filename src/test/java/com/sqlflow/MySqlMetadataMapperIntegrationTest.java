package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

/**
 * 조회 전용 MySQL 계정으로 메타데이터 조회가 가능한지 확인한다.
 * 테이블 생성이나 데이터 변경은 하지 않는다.
 */
@SpringBootTest
class MySqlMetadataMapperIntegrationTest {

    private static final String DATABASE = "sqlflow_demo";
    private static final String TABLE = "connection_test";

    @Autowired
    private MySqlMetadataMapper metadataMapper;

    @Test
    void readsCurrentDatabase() {
        assertEquals(DATABASE, metadataMapper.selectCurrentDatabase());
    }

    @Test
    void readsIdentifierCaseSetting() {
        int mode = metadataMapper.selectLowerCaseTableNames();

        assertTrue(Set.of(0, 1, 2).contains(mode),
                "예상하지 못한 lower_case_table_names 값: " + mode);
    }

    @Test
    void findsExistingTable() {
        assertEquals(
                1,
                metadataMapper.countSelectableObjects(DATABASE, TABLE));
    }

    @Test
    void readsActualColumnsInDeclarationOrder() {
        var columns = metadataMapper.selectColumns(DATABASE, TABLE);

        assertFalse(columns.isEmpty());

        Set<String> names = columns.stream()
                .map(column -> column.getColumnName())
                .collect(Collectors.toSet());

        assertTrue(names.containsAll(Set.of("id", "message")));

        int previousPosition = 0;

        for (var column : columns) {
            assertEquals(DATABASE, column.getDatabaseName());
            assertEquals(TABLE, column.getTableName());

            assertNotNull(column.getColumnName());
            assertFalse(column.getColumnName().isBlank());

            assertNotNull(column.getDataType());
            assertFalse(column.getDataType().isBlank());

            assertNotNull(column.getOrdinalPosition());
            assertTrue(column.getOrdinalPosition() > previousPosition);

            previousPosition = column.getOrdinalPosition();
        }
    }

    @Test
    void missingObjectReturnsNoMetadata() {
        String missing = "missing_" + UUID.randomUUID()
                .toString().replace("-", "");

        assertEquals(
                0,
                metadataMapper.countSelectableObjects(DATABASE, missing));

        assertTrue(
                metadataMapper.selectColumns(DATABASE, missing).isEmpty());
    }
    
    @Test
    void resolvesExistingObjectToActualName() {
        var object = metadataMapper.selectObject(
                "sqlflow_demo", "connection_test");

        assertNotNull(object);
        assertEquals("sqlflow_demo", object.getDatabaseName());
        assertEquals("connection_test", object.getObjectName());
        assertEquals("BASE TABLE", object.getObjectType());
    }

    @Test
    void objectResolutionFollowsServerCaseSetting() {
        int mode = metadataMapper.selectLowerCaseTableNames();

        var object = metadataMapper.selectObject(
                "SQLFLOW_DEMO", "CONNECTION_TEST");

        if (mode == 0) {
            // 대소문자를 구분하는 서버에서는 다른 이름이다.
            assertEquals(null, object);
        } else {
            assertNotNull(object);
            assertEquals("sqlflow_demo", object.getDatabaseName());
            assertEquals("connection_test", object.getObjectName());
        }
    }
}