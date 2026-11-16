package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.execution.service.*;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.service.MySqlTableValidationService;
import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService.*;

class DmlSqlValidationServiceTest {
    private SqlExplainValidationService explain;
    private MySqlTableValidationService tables;
    private DataAccessValidationService access;
    private DmlSqlValidationService service;
    @BeforeEach void setUp() {
        explain = mock(SqlExplainValidationService.class);
        tables = mock(MySqlTableValidationService.class);
        access = mock(DataAccessValidationService.class);
        service = new DmlSqlValidationService(new SqlBasicValidator(), explain, tables, access);
    }
    @Test void draftDoesNotNeedDatabase() {
        assertEquals(Status.VALID, service.validate(SqlType.INSERT, "INSERT INTO t VALUES (1);").status());
        assertEquals(Status.BLOCKED, service.validate(SqlType.UPDATE, "DELETE FROM t;").status());
        assertEquals(Status.BLOCKED, service.validate(SqlType.DELETE, "DELETE FROM t").status());
        verifyNoInteractions(explain, tables, access);
    }
    @Test void accessDeniedIsWarningAndBothInsertSelectTargetsAreChecked() {
        String sql = "INSERT INTO target SELECT * FROM source;";
        var target = new QueryObjectName("demo", "target");
        var source = new QueryObjectName("demo", "source");
        when(explain.validate("INSERT", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.VALID, "OK"));
        when(tables.validateStatementTables(any())).thenReturn(Map.of("target", target, "source", source));
        when(access.validate(1L, Set.of(target, source))).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음"));
        var result = service.validateForReview(1L, SqlType.INSERT, sql);
        assertEquals(Status.WARNING, result.status());
        assertTrue(result.message().contains("권한 없음"));
        verify(access).validate(1L, Set.of(target, source));
    }
    @Test void clearSyntaxErrorBlocksButUnavailableDatabaseOnlyWarns() {
        String sql = "DELETE FROM t;";
        when(explain.validate("DELETE", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.SYNTAX_ERROR, "syntax"));
        assertEquals(Status.BLOCKED, service.validateForReview(1L, SqlType.DELETE, sql).status());
        verifyNoInteractions(tables, access);
        when(explain.validate("DELETE", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.INCOMPLETE, "offline"));
        when(tables.validateStatementTables(any())).thenReturn(Map.of());
        var result = service.validateForReview(1L, SqlType.DELETE, sql);
        assertEquals(Status.WARNING, result.status());
        assertTrue(result.message().contains("WHERE"));
    }
    @Test void realObjectExtractionIncludesInsertTargetAndSelectSource() throws Exception {
        var mapper = mock(com.sqlflow.mysql.mapper.MySqlMetadataMapper.class);
        var tableService = new MySqlTableValidationService(new SqlTableExtractor(), mapper);
        when(mapper.selectCurrentDatabase()).thenReturn("demo");
        when(mapper.selectObject(anyString(), anyString())).thenAnswer(call -> {
            var metadata = new com.sqlflow.mysql.domain.MySqlObjectMetadata();
            metadata.setDatabaseName(call.getArgument(0));
            metadata.setObjectName(call.getArgument(1));
            return metadata;
        });
        var statement = net.sf.jsqlparser.parser.CCJSqlParserUtil.parse("INSERT INTO target SELECT * FROM source;");
        var targets = tableService.validateStatementTables(statement);
        assertEquals(Set.of(new QueryObjectName("demo", "target"), new QueryObjectName("demo", "source")), Set.copyOf(targets.values()));
    }

}
