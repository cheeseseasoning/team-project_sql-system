package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.execution.domain.OracleSynonymMetadata;
import com.sqlflow.execution.mapper.OracleMetadataMapper;

/**
 * Oracle이 관리하는 동의어의 참조 대상 조회를 검증한다.
 * DB 구조나 업무 데이터는 변경하지 않는다.
 */
@SpringBootTest
class OracleSynonymMapperTest {

    @Autowired
    private OracleMetadataMapper oracleMetadataMapper;

    @Test
    void publicDualSynonymPointsToSysDual() {
        OracleSynonymMetadata synonym = oracleMetadataMapper.selectSynonym("PUBLIC", "DUAL");

        assertNotNull(synonym, "공용 DUAL 동의어를 조회할 수 있어야 합니다.");
        assertEquals("SYS", synonym.getTableOwner());
        assertEquals("DUAL", synonym.getTableName());
        assertNull(synonym.getDbLink());
    }

    @Test
    void missingSynonymReturnsNull() {
        String schema = oracleMetadataMapper.selectCurrentSchema();

        OracleSynonymMetadata synonym = oracleMetadataMapper.selectSynonym(
                schema, "SQLFLOW_TEST_MISSING_SYNONYM");

        assertNull(synonym);
    }
}