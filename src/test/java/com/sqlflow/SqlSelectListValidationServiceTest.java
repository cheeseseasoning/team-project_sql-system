package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.domain.OracleSynonymMetadata;
import com.sqlflow.execution.mapper.OracleMetadataMapper;
import com.sqlflow.execution.service.OracleColumnValidationService;
import com.sqlflow.execution.service.OracleObjectNameResolver;
import com.sqlflow.execution.service.OracleTableValidationService;
import com.sqlflow.execution.service.SqlColumnQualifierValidator;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlSelectColumnExtractor;
import com.sqlflow.execution.service.SqlSelectListValidationService;
import com.sqlflow.execution.service.SqlTableExtractor;

/**
 * SELECT 목록 검증의 연결 흐름을 확인한다.
 * Oracle 조회만 Mock으로 대체하며 DB·Spring 서버는 실행하지 않는다.
 */
@ExtendWith(MockitoExtension.class)
class SqlSelectListValidationServiceTest {

    @Mock
    private OracleMetadataMapper metadataMapper;

    private SqlSelectListValidationService service;

    @BeforeEach
    void setUp() {
        OracleTableValidationService tableService =
                new OracleTableValidationService(
                        new SqlTableExtractor(),
                        new OracleObjectNameResolver(),
                        metadataMapper);

        OracleColumnValidationService columnService =
                new OracleColumnValidationService(metadataMapper);

        SqlSelectColumnExtractor columnExtractor =
                new SqlSelectColumnExtractor(new SqlColumnQualifierValidator());

        service = new SqlSelectListValidationService(
                new SqlParserValidator(),
                columnExtractor,
                tableService,
                columnService);
    }

    @Test
    void aliasAndMultipleColumnsAreValidatedTogether() {
        prepareObject("SQLFLOW", "USERS",
                List.of(column("SQLFLOW", "USERS", "USER_ID"),
                        column("SQLFLOW", "USERS", "USER_NAME")));

        assertDoesNotThrow(() -> service.validateSelectList(
                "SELECT U.USER_ID, U.USER_NAME AS NAME FROM USERS U"));

        // 별칭이나 출력 이름이 아닌 실제 테이블의 컬럼 목록을 조회한다.
        verify(metadataMapper).selectColumns("SQLFLOW", "USERS");
    }

    @Test
    void explicitSchemaIsUsedForColumnLookup() {
        prepareObject("HR", "EMPLOYEES",
                List.of(column("HR", "EMPLOYEES", "EMPLOYEE_ID")));

        assertDoesNotThrow(() -> service.validateSelectList(
                "SELECT E.EMPLOYEE_ID FROM HR.EMPLOYEES E"));

        verify(metadataMapper).selectColumns("HR", "EMPLOYEES");
    }

    @Test
    void quotedColumnCaseIsPreservedThroughTheFlow() {
        prepareObject("SQLFLOW", "USERS",
                List.of(column("SQLFLOW", "USERS", "UserName")));

        assertDoesNotThrow(() -> service.validateSelectList(
                "SELECT U.\"UserName\" FROM USERS U"));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList(
                        "SELECT U.\"USERNAME\" FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void missingColumnIsRejectedAfterObjectResolution() {
        prepareObject("SQLFLOW", "USERS",
                List.of(column("SQLFLOW", "USERS", "USER_ID")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList(
                        "SELECT U.UNKNOWN_COLUMN FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "조회 대상에 존재하지 않는 컬럼입니다: UNKNOWN_COLUMN",
                exception.getReason());
    }

    @Test
    void missingObjectStopsBeforeColumnLookup() {
        when(metadataMapper.selectCurrentSchema()).thenReturn("SQLFLOW");
        when(metadataMapper.countSelectableObjects("SQLFLOW", "MISSING_TABLE"))
                .thenReturn(0);
        when(metadataMapper.selectSynonym("SQLFLOW", "MISSING_TABLE"))
                .thenReturn(null);
        when(metadataMapper.selectSynonym("PUBLIC", "MISSING_TABLE"))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList(
                        "SELECT USER_ID FROM MISSING_TABLE"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        // 대상 확인에 실패했으면 컬럼 조회로 진행하지 않아야 한다.
        verify(metadataMapper, never()).selectColumns(anyString(), anyString());
    }

    @Test
    void invalidAliasStopsBeforeMetadataLookup() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList(
                        "SELECT X.USER_NAME FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(metadataMapper);
    }

    @Test
    void nonSelectStopsBeforeMetadataLookup() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList("DELETE FROM USERS"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(metadataMapper);
    }

    @Test
    void unsupportedWildcardStopsBeforeMetadataLookup() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateSelectList("SELECT * FROM USERS"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(metadataMapper);
    }

    @Test
    void synonymUsesActualTargetForColumnLookup() {
        when(metadataMapper.selectCurrentSchema()).thenReturn("SQLFLOW");

        // STAFF는 테이블이 아니라 HR.EMPLOYEES를 가리키는 개인 동의어다.
        doReturn(0).when(metadataMapper)
                .countSelectableObjects("SQLFLOW", "STAFF");
        doReturn(1).when(metadataMapper)
                .countSelectableObjects("HR", "EMPLOYEES");

        OracleSynonymMetadata synonym = new OracleSynonymMetadata();
        synonym.setTableOwner("HR");
        synonym.setTableName("EMPLOYEES");

        when(metadataMapper.selectSynonym("SQLFLOW", "STAFF"))
                .thenReturn(synonym);
        when(metadataMapper.selectColumns("HR", "EMPLOYEES"))
                .thenReturn(List.of(column("HR", "EMPLOYEES", "EMPLOYEE_ID")));

        assertDoesNotThrow(() -> service.validateSelectList(
                "SELECT S.EMPLOYEE_ID FROM STAFF S"));

        // 동의어 이름이 아니라 해석된 실제 대상에서 컬럼을 확인해야 한다.
        verify(metadataMapper).selectColumns("HR", "EMPLOYEES");
        verify(metadataMapper, never()).selectColumns("SQLFLOW", "STAFF");
    }

    /** 현재 스키마와 실제 객체·컬럼 조회 결과를 준비한다. */
    private void prepareObject(String owner, String objectName,
            List<OracleColumnMetadata> columns) {
        when(metadataMapper.selectCurrentSchema()).thenReturn("SQLFLOW");
        when(metadataMapper.countSelectableObjects(owner, objectName))
                .thenReturn(1);
        when(metadataMapper.selectColumns(owner, objectName))
                .thenReturn(columns);
    }

    /** Oracle에서 반환한 컬럼 정보에 해당하는 테스트 데이터를 만든다. */
    private OracleColumnMetadata column(String owner, String tableName,
            String columnName) {
        OracleColumnMetadata column = new OracleColumnMetadata();
        column.setOwner(owner);
        column.setTableName(tableName);
        column.setColumnName(columnName);
        column.setDataType("VARCHAR2");
        return column;
    }
}