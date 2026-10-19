package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.mapper.OracleMetadataMapper;

/**
 * Oracle 데이터 사전에서 스키마·객체·컬럼 정보를 조회하는지 검증한다.
 * 기존 업무 데이터와 DB 구조를 변경하지 않는다.
 */
@SpringBootTest
class OracleMetadataMapperTest {

    @Autowired
    private OracleMetadataMapper oracleMetadataMapper;

    @Test
    void currentSchemaIsReturned() {
        String schema = oracleMetadataMapper.selectCurrentSchema();

        assertNotNull(schema);
        assertFalse(schema.isBlank());
    }

    @Test
    void usersTableAndItsColumnsAreReturned() {
        String schema = oracleMetadataMapper.selectCurrentSchema();

        int objectCount = oracleMetadataMapper.countSelectableObjects(schema, "USERS");

        assertTrue(objectCount > 0, "현재 스키마에서 USERS 테이블을 찾을 수 있어야 합니다.");

        List<OracleColumnMetadata> columns = oracleMetadataMapper.selectColumns(schema, "USERS");

        assertFalse(columns.isEmpty());

        // 다른 객체의 컬럼이 섞이지 않고 이름·자료형이 매핑됐는지 확인한다.
        for (OracleColumnMetadata column : columns) {
            assertEquals(schema, column.getOwner());
            assertEquals("USERS", column.getTableName());
            assertNotNull(column.getColumnName());
            assertNotNull(column.getDataType());
        }

        // 실제 프로젝트에서 사용하는 대표 컬럼이 포함되어야 한다.
        OracleColumnMetadata userId = columns.stream()
                .filter(column -> "USER_ID".equals(column.getColumnName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("USERS.USER_ID 컬럼을 찾을 수 없습니다."));

        assertEquals("NUMBER", userId.getDataType());

        assertTrue(columns.stream()
                .anyMatch(column -> "USER_NAME".equals(column.getColumnName())));
    }

    @Test
    void missingObjectReturnsZeroAndEmptyColumns() {
        String schema = oracleMetadataMapper.selectCurrentSchema();

        // 프로젝트에서 사용하지 않는 테스트용 이름으로 조회한다.
        String objectName = "SQLFLOW_METADATA_TEST_MISSING";

        int objectCount = oracleMetadataMapper.countSelectableObjects(schema, objectName);
        List<OracleColumnMetadata> columns = oracleMetadataMapper.selectColumns(schema, objectName);

        assertEquals(0, objectCount);
        assertTrue(columns.isEmpty());
    }
}