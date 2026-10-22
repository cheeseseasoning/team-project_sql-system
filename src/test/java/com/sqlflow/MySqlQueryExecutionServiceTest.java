package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sqlflow.execution.service.MySqlQueryExecutionService;

/**
 * JDBC 응답은 Mock으로 구성하고 CSV 파일은 실제 임시 폴더에 저장한다.
 * 실제 MySQL 연결 및 읽기 전용 계정 권한은 별도 연동 테스트에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class MySqlQueryExecutionServiceTest {

    private static final String SQL = "SELECT customer_name FROM demo_customer";

    @TempDir
    Path resultDirectory;

    @Mock
    DataSource dataSource;

    @Mock
    Connection connection;

    @Mock
    Statement statement;

    @Mock
    ResultSet resultSet;

    @Mock
    ResultSetMetaData metadata;

    private MySqlQueryExecutionService service;

    @BeforeEach
    void setUp() {
        service = createService(1_048_576);
    }

    @Test
    void savesCsvAndReturnsFileInformation() throws Exception {
        prepareResult("고객명", "메모");

        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString(1)).thenReturn("홍길동", null);
        when(resultSet.getString(2)).thenReturn(
                "쉼표, 큰따옴표\"와\n줄바꿈",
                "");

        var result = service.execute(SQL);

        Path file = resultDirectory.resolve(result.relativePath());
        String csv = Files.readString(file, StandardCharsets.UTF_8);

        String expected = "\uFEFF\"고객명\",\"메모\"\r\n"
                + "\"홍길동\",\"쉼표, 큰따옴표\"\"와\n줄바꿈\"\r\n"
                + "\"\",\"\"\r\n";

        assertEquals(expected, csv);
        assertEquals(2L, result.rowCount());
        assertEquals(Files.size(file), result.fileSize());
        assertFalse(Path.of(result.relativePath()).isAbsolute());
        assertTrue(result.durationMs() >= 0);

        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void emptyResultStillSavesColumnHeader() throws Exception {
        prepareResult("고객명");
        when(resultSet.next()).thenReturn(false);

        var result = service.execute(SQL);

        String csv = Files.readString(
                resultDirectory.resolve(result.relativePath()),
                StandardCharsets.UTF_8);

        assertEquals("\uFEFF\"고객명\"\r\n", csv);
        assertEquals(0L, result.rowCount());
    }

    @Test
    void sqlFailureDeletesCreatedFile() throws Exception {
        prepareStatement();

        SQLException failure = new SQLException("조회 실패", "42000", 1064);
        when(statement.executeQuery(SQL)).thenThrow(failure);

        SQLException actual = assertThrows(
                SQLException.class,
                () -> service.execute(SQL));

        assertSame(failure, actual);
        assertNoResultFiles();

        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void readFailureDeletesPartiallyWrittenFile() throws Exception {
        prepareResult("고객명");

        SQLException failure = new SQLException("결과 읽기 중 연결 끊김");

        // 첫 행은 저장되지만 다음 행을 읽는 과정에서 실패한다.
        when(resultSet.next()).thenReturn(true).thenThrow(failure);
        when(resultSet.getString(1)).thenReturn("홍길동");

        SQLException actual = assertThrows(
                SQLException.class,
                () -> service.execute(SQL));

        assertSame(failure, actual);
        assertNoResultFiles();

        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void fileSizeLimitDeletesIncompleteFile() throws Exception {
        // BOM과 제목은 들어가지만 데이터 행을 넣으면 초과하는 크기.
        service = createService(12);

        prepareResult("name");
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString(1)).thenReturn("a".repeat(100));

        IOException exception = assertThrows(
                IOException.class,
                () -> service.execute(SQL));

        assertTrue(exception.getMessage().contains("최대 크기"));
        assertNoResultFiles();

        verify(resultSet).close();
        verify(statement).close();
        verify(connection).close();
    }

    @Test
    void blankSqlIsRejectedBeforeConnecting() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.execute(null));
        assertThrows(IllegalArgumentException.class, () -> service.execute("  "));

        verifyNoInteractions(dataSource);
        assertNoResultFiles();
    }

    @Test
    void zeroLimitsAllowExecutionAndFileCreation() throws Exception {
        service = new MySqlQueryExecutionService(
                dataSource, resultDirectory.toString(), 0, 0);

        prepareResult("name");
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString(1)).thenReturn("a".repeat(100));

        var result = service.execute(SQL);

        assertEquals(1L, result.rowCount());
        assertTrue(result.fileSize() > 100);
        assertTrue(Files.exists(resultDirectory.resolve(result.relativePath())));

        // JDBC에도 제한 없음 값이 전달되는지 확인한다.
        verify(statement).setQueryTimeout(0);
    }

    @Test
    void negativeLimitsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlQueryExecutionService(
                        dataSource, resultDirectory.toString(), -1, 0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MySqlQueryExecutionService(
                        dataSource, resultDirectory.toString(), 0, -1));
    }

    private MySqlQueryExecutionService createService(long maxFileBytes) {
        return new MySqlQueryExecutionService(
                dataSource,
                resultDirectory.toString(),
                60,
                maxFileBytes);
    }

    /** 해당 테스트에서 필요한 JDBC 응답만 설정해 불필요한 Mock 설정을 피한다. */
    private void prepareStatement() throws SQLException {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement(
                ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY))
                .thenReturn(statement);
    }

    private void prepareResult(String... columnLabels) throws SQLException {
        prepareStatement();

        when(statement.executeQuery(SQL)).thenReturn(resultSet);
        when(resultSet.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(columnLabels.length);

        for (int index = 0; index < columnLabels.length; index++) {
            when(metadata.getColumnLabel(index + 1)).thenReturn(columnLabels[index]);
        }
    }

    private void assertNoResultFiles() throws IOException {
        try (var files = Files.list(resultDirectory)) {
            assertEquals(0L, files.count(), "실패한 실행의 결과 파일이 남으면 안 됩니다.");
        }
    }
}
