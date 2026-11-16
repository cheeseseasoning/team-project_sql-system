package com.sqlflow.execution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class SqlExplainValidationServiceTest {
    @Test void usesOnlyPlainExplainAndClosesResources() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("EXPLAIN UPDATE t SET id=1;")).thenReturn(result);
        var service = new SqlExplainValidationService(source, source, new SqlBasicValidator());
        assertEquals(SqlExplainValidationService.State.VALID, service.validate("UPDATE", "UPDATE t SET id=1;").state());
        verify(statement).executeQuery("EXPLAIN UPDATE t SET id=1;");
        verify(statement, never()).execute(anyString());
        verify(statement, never()).executeUpdate(anyString());
        verify(connection).setReadOnly(false);
        verify(connection).close();
        verify(statement).close();
        verify(result).close();
    }
    @Test void classifiesSyntaxObjectAndConnectionErrorsSeparately() {
        var service = new SqlExplainValidationService(mock(DataSource.class), null, new SqlBasicValidator());
        assertEquals(SqlExplainValidationService.State.SYNTAX_ERROR, service.classify(new SQLException("syntax", "42000", 1064)).state());
        assertEquals(SqlExplainValidationService.State.OBJECT_ERROR, service.classify(new SQLException("table", "42S02", 1146)).state());
        assertEquals(SqlExplainValidationService.State.OBJECT_ERROR, service.classify(new SQLException("column", "42S22", 1054)).state());
        assertEquals(SqlExplainValidationService.State.INCOMPLETE, service.classify(new SQLException("denied", "42000", 1142)).state());
        assertEquals(SqlExplainValidationService.State.INCOMPLETE, service.classify(new SQLException("connection", "08001", 0)).state());
    }
    @Test void doesNotContactDatabaseForMultipleStatements() {
        DataSource source = mock(DataSource.class);
        var service = new SqlExplainValidationService(source, source, new SqlBasicValidator());
        assertEquals(SqlExplainValidationService.State.SYNTAX_ERROR, service.validate("SELECT", "SELECT * FROM t; DELETE FROM t;").state());
        verifyNoInteractions(source);
    }
    @Test void selectUsesOnlyReaderConnection() throws Exception {
        DataSource reader = mock(DataSource.class), validation = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet result = mock(ResultSet.class);
        when(reader.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("EXPLAIN SELECT * FROM t;")).thenReturn(result);
        var service = new SqlExplainValidationService(reader, validation, new SqlBasicValidator());
        assertEquals(SqlExplainValidationService.State.VALID, service.validate("SELECT", "SELECT * FROM t;").state());
        verify(connection).setReadOnly(true);
        verifyNoInteractions(validation);
    }
    @Test void dmlUsesOnlyValidationConnection() throws Exception {
        for (String sql : java.util.List.of("INSERT INTO t VALUES (1);", "UPDATE t SET id=1;", "DELETE FROM t;")) {
            DataSource reader = mock(DataSource.class), validation = mock(DataSource.class);
            Connection connection = mock(Connection.class);
            Statement statement = mock(Statement.class);
            when(validation.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery("EXPLAIN " + sql)).thenReturn(mock(ResultSet.class));
            var service = new SqlExplainValidationService(reader, validation, new SqlBasicValidator());
            assertEquals(SqlExplainValidationService.State.VALID, service.validate(sql.split(" ")[0], sql).state());
            verifyNoInteractions(reader);
            verify(connection).setReadOnly(false);
            verify(statement).executeQuery("EXPLAIN " + sql);
            verify(statement, never()).execute(anyString());
            verify(statement, never()).executeUpdate(anyString());
            verify(statement, never()).executeQuery(sql);
        }
    }
    @Test void missingValidationConnectionDoesNotFallBackToReader() {
        DataSource reader = mock(DataSource.class);
        var service = new SqlExplainValidationService(reader, null, new SqlBasicValidator());
        assertEquals(SqlExplainValidationService.State.INCOMPLETE, service.validate("DELETE", "DELETE FROM t;").state());
        verifyNoInteractions(reader);
    }
    @Test void explainsTheCauseAndNextActionWithoutChangingErrorStates() {
        var service = new SqlExplainValidationService(mock(DataSource.class), null, new SqlBasicValidator());
        var syntax = service.classify(new SQLException("You have an error in your SQL syntax", "42000", 1064));
        assertEquals(SqlExplainValidationService.State.SYNTAX_ERROR, syntax.state());
        assertTrue(syntax.message().contains("키워드, 쉼표, 괄호"));
        var column = service.classify(new SQLException("Unknown column", "42S22", 1054));
        assertEquals(SqlExplainValidationService.State.OBJECT_ERROR, column.state());
        assertTrue(column.message().contains("컬럼명과 테이블 별칭"));
        var denied = service.classify(new SQLException("command denied", "42000", 1142));
        assertEquals(SqlExplainValidationService.State.INCOMPLETE, denied.state());
        assertTrue(denied.message().contains("DB팀에 계정 권한 확인"));
        assertTrue(denied.message().contains("1142"));
    }
}
