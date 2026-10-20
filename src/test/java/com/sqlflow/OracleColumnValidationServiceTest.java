package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.mapper.OracleMetadataMapper;
import com.sqlflow.execution.service.OracleColumnValidationService;

/**
 * Oracle 접속 없이 컬럼 이름 비교와 오류 처리 규칙을 검증한다.
 * 실제 메타데이터 조회 SQL은 Mapper 연동 테스트에서 별도로 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class OracleColumnValidationServiceTest {

    @Mock
    private OracleMetadataMapper oracleMetadataMapper;

    @InjectMocks
    private OracleColumnValidationService service;

    private final OracleObjectName target = new OracleObjectName("SQLFLOW", "USERS");

    @Test
    void existingColumnsAreAcceptedWithOneMetadataQuery() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER_ID"), column("USER_NAME")));

        service.validateColumns(target, Set.of("USER_ID", "USER_NAME"));

        // 여러 컬럼을 검사해도 해당 객체의 메타데이터는 한 번만 조회한다.
        verify(oracleMetadataMapper).selectColumns("SQLFLOW", "USERS");
        verifyNoMoreInteractions(oracleMetadataMapper);
    }

    @Test
    void unquotedColumnNameIsConvertedToUppercase() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER_NAME")));

        service.validateColumns(target, Set.of("user_name"));
    }

    @Test
    void quotedColumnNamePreservesCase() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("UserName")));

        service.validateColumns(target, Set.of("\"UserName\""));
    }

    @Test
    void quotedColumnWithDifferentCaseIsRejected() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER_NAME")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateColumns(target, Set.of("\"user_name\"")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "조회 대상에 존재하지 않는 컬럼입니다: \"user_name\"",
                exception.getReason());
    }

    @Test
    void escapedDoubleQuoteInColumnNameIsResolved() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER\"NAME")));

        // SQL 식별자 안의 큰따옴표 두 개는 실제 이름의 큰따옴표 한 개다.
        service.validateColumns(target, Set.of("\"USER\"\"NAME\""));
    }

    @Test
    void missingColumnIsRejected() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER_ID")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateColumns(target, Set.of("UNKNOWN_COLUMN")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "조회 대상에 존재하지 않는 컬럼입니다: UNKNOWN_COLUMN",
                exception.getReason());
    }

    @Test
    void emptyMetadataIsRejected() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateColumns(target, Set.of("USER_ID")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "조회 대상의 컬럼 정보를 확인할 수 없습니다.",
                exception.getReason());
    }

    @Test
    void blankColumnNameIsRejected() {
        when(oracleMetadataMapper.selectColumns("SQLFLOW", "USERS"))
                .thenReturn(List.of(column("USER_ID")));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateColumns(target, Set.of(" ")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("검사할 컬럼 이름이 없습니다.", exception.getReason());
    }

    @Test
    void nullTargetIsRejectedBeforeMetadataQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateColumns(null, Set.of("USER_ID")));

        verifyNoInteractions(oracleMetadataMapper);
    }

    @Test
    void nullColumnNamesAreRejectedBeforeMetadataQuery() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateColumns(target, null));

        verifyNoInteractions(oracleMetadataMapper);
    }

    /** DB 조회 결과를 대신할 컬럼 메타데이터를 만든다. */
    private OracleColumnMetadata column(String columnName) {
        OracleColumnMetadata column = new OracleColumnMetadata();
        column.setOwner("SQLFLOW");
        column.setTableName("USERS");
        column.setColumnName(columnName);
        column.setDataType("VARCHAR2");
        return column;
    }
}