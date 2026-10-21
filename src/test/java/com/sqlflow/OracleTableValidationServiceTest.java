package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.domain.OracleSynonymMetadata;
import com.sqlflow.execution.mapper.OracleMetadataMapper;
import com.sqlflow.execution.service.OracleObjectNameResolver;
import com.sqlflow.execution.service.OracleTableValidationService;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlTableExtractor;

/**
 * 실제 파싱·이름 해석 코드와 테스트용 DB 조회 결과를 조합한다.
 * 동의어 생성 없이 대상 연결 및 차단 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class OracleTableValidationServiceTest {

    @Mock
    private OracleMetadataMapper oracleMetadataMapper;

    private final SqlParserValidator parser = new SqlParserValidator();

    private OracleTableValidationService service;

    @BeforeEach
    void setUp() {
        service = new OracleTableValidationService(
                new SqlTableExtractor(),
                new OracleObjectNameResolver(),
                oracleMetadataMapper);

        when(oracleMetadataMapper.selectCurrentSchema()).thenReturn("SQLFLOW");
    }

    @Test
    void existingTableIsResolved() {
        when(oracleMetadataMapper.countSelectableObjects("SQLFLOW", "USERS")).thenReturn(1);

        Map<String, OracleObjectName> result = validate("SELECT * FROM USERS");

        assertEquals(new OracleObjectName("SQLFLOW", "USERS"), result.get("USERS"));
        verify(oracleMetadataMapper, never()).selectSynonym(anyString(), anyString());
    }

    @Test
    void privateSynonymIsResolvedBeforePublicSynonym() {
        // STAFF는 실제 테이블이 아니라 USERS를 가리키는 개인 동의어다.
        doReturn(0).when(oracleMetadataMapper).countSelectableObjects("SQLFLOW", "STAFF");
        doReturn(1).when(oracleMetadataMapper).countSelectableObjects("SQLFLOW", "USERS");

        doReturn(synonym("SQLFLOW", "USERS", null))
                .when(oracleMetadataMapper).selectSynonym("SQLFLOW", "STAFF");

        Map<String, OracleObjectName> result = validate("SELECT * FROM STAFF");

        assertEquals(new OracleObjectName("SQLFLOW", "USERS"), result.get("STAFF"));
        verify(oracleMetadataMapper, never()).selectSynonym("PUBLIC", "STAFF");
    }

    @Test
    void publicSynonymIsResolved() {
        // 현재 스키마에는 DUAL 테이블과 개인 동의어가 없는 상황이다.
        doReturn(0).when(oracleMetadataMapper).countSelectableObjects("SQLFLOW", "DUAL");
        doReturn(null).when(oracleMetadataMapper).selectSynonym("SQLFLOW", "DUAL");

        // 공용 동의어는 SYS.DUAL을 가리키고, 그 대상 테이블은 존재한다.
        doReturn(synonym("SYS", "DUAL", null))
                .when(oracleMetadataMapper).selectSynonym("PUBLIC", "DUAL");
        doReturn(1).when(oracleMetadataMapper).countSelectableObjects("SYS", "DUAL");

        Map<String, OracleObjectName> result = validate("SELECT 1 FROM DUAL");

        assertEquals(new OracleObjectName("SYS", "DUAL"), result.get("DUAL"));
        verify(oracleMetadataMapper).selectSynonym("SQLFLOW", "DUAL");
        verify(oracleMetadataMapper).selectSynonym("PUBLIC", "DUAL");
    }

    @Test
    void explicitSchemaDoesNotFallBackToPublicSynonym() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validate("SELECT * FROM HR.MISSING_TABLE"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(oracleMetadataMapper).selectSynonym("HR", "MISSING_TABLE");
        verify(oracleMetadataMapper, never()).selectSynonym("PUBLIC", "MISSING_TABLE");
    }

    @Test
    void missingObjectIsRejected() {
    	// 별도 설정하지 않은 Mock 조회는 개수 0 또는 null을 반환한다.
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validate("SELECT * FROM MISSING_TABLE"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(oracleMetadataMapper).selectSynonym("SQLFLOW", "MISSING_TABLE");
        verify(oracleMetadataMapper).selectSynonym("PUBLIC", "MISSING_TABLE");
    }

    @Test
    void circularSynonymsAreRejected() {
        when(oracleMetadataMapper.selectSynonym("SQLFLOW", "LINK_A"))
                .thenReturn(synonym("SQLFLOW", "LINK_B", null));
        when(oracleMetadataMapper.selectSynonym("SQLFLOW", "LINK_B"))
                .thenReturn(synonym("SQLFLOW", "LINK_A", null));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validate("SELECT * FROM LINK_A"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("동의어가 순환 참조되어 대상을 확인할 수 없습니다.", exception.getReason());
    }

    @Test
    void remoteSynonymIsRejected() {
        when(oracleMetadataMapper.selectSynonym("SQLFLOW", "REMOTE_USERS"))
                .thenReturn(synonym("HR", "USERS", "REMOTE_DB"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validate("SELECT * FROM REMOTE_USERS"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("원격 DB 링크를 사용하는 조회 대상은 지원하지 않습니다.", exception.getReason());

        // 원격 대상으로 이어서 조회하지 않았는지도 확인한다.
        verify(oracleMetadataMapper, never()).countSelectableObjects("HR", "USERS");
    }

    private Map<String, OracleObjectName> validate(String sqlText) {
        return service.validateTables(parser.parseSelect(sqlText));
    }

    /** DB에서 조회한 동의어 정보를 대신 구성한다. */
    private OracleSynonymMetadata synonym(String owner, String tableName, String dbLink) {
        OracleSynonymMetadata result = new OracleSynonymMetadata();
        result.setTableOwner(owner);
        result.setTableName(tableName);
        result.setDbLink(dbLink);

        return result;
    }
}