package com.sqlflow.execution.service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 검증된 SQL 한 개를 MySQL에서 실행하고 결과를 CSV 파일로 저장한다.
 *
 * 실행 권한·접근 정책·승인 상태 검사는 호출하는 서비스가 먼저 수행한다.
 * 이 서비스는 Oracle 실행 이력이나 결과 메타데이터를 저장하지 않는다.
 */
@Service
public class MySqlQueryExecutionService {

    private final DataSource queryDataSource;
    private final Path resultDirectory;
    private final int queryTimeoutSeconds;
    private final long maxFileBytes;

    public MySqlQueryExecutionService(
            @Qualifier("queryDataSource") DataSource queryDataSource,
            @Value("${app.execution.result-directory}") String resultDirectory,
            @Value("${app.execution.query-timeout-seconds}") int queryTimeoutSeconds,
            @Value("${app.execution.max-file-bytes}") long maxFileBytes) {

        if (queryTimeoutSeconds <= 0 || maxFileBytes <= 0) {
            throw new IllegalArgumentException("실행 제한 시간과 파일 크기는 양수여야 합니다.");
        }

        this.queryDataSource = queryDataSource;
        this.resultDirectory = Path.of(resultDirectory).toAbsolutePath().normalize();
        this.queryTimeoutSeconds = queryTimeoutSeconds;
        this.maxFileBytes = maxFileBytes;
    }

    /**
     * 관리 DB에서 읽고 검증을 마친 SQL 원문을 전달받는다.
     * 실패하면 생성 중인 파일을 삭제하고 원래 예외를 전달한다.
     */
    public ExecutionResult execute(String sqlText) throws SQLException, IOException {

        if (sqlText == null || sqlText.isBlank()) {
            throw new IllegalArgumentException("실행할 SQL이 없습니다.");
        }

        long startedNanos = System.nanoTime();

        Files.createDirectories(resultDirectory);

        // 실행마다 새 파일을 생성해 기존 결과를 덮어쓰지 않는다.
        Path resultFile = Files.createTempFile(resultDirectory, "query-", ".csv");

        try {
            long rowCount = writeResult(sqlText, resultFile);
            long fileSize = Files.size(resultFile);
            long durationMs = (System.nanoTime() - startedNanos) / 1_000_000;

            // 공통 저장 폴더 기준의 상대 경로만 반환한다.
            String relativePath = resultDirectory.relativize(resultFile)
                    .toString()
                    .replace('\\', '/');

            return new ExecutionResult(relativePath, rowCount, fileSize, durationMs);
        } catch (SQLException | IOException | RuntimeException exception) {
            deleteIncompleteFile(resultFile, exception);
            throw exception;
        }
    }

    /**
     * 전체 결과를 List에 모으지 않고 한 행씩 읽어 파일에 기록한다.
     * Oracle 트랜잭션과 분리된 MySQL 연결을 직접 사용한다.
     */
    private long writeResult(String sqlText, Path resultFile)
            throws SQLException, IOException {

        try (Connection connection = queryDataSource.getConnection()) {
            connection.setReadOnly(true);

            try (Statement statement = connection.createStatement(
                    ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

                statement.setQueryTimeout(queryTimeoutSeconds);

                // MySQL Connector/J에서 결과를 순차적으로 읽는 모드를 사용한다.
                statement.setFetchSize(Integer.MIN_VALUE);

                try (ResultSet resultSet = statement.executeQuery(sqlText);
                     OutputStream output = Files.newOutputStream(resultFile)) {

                    ResultSetMetaData metadata = resultSet.getMetaData();
                    int columnCount = metadata.getColumnCount();

                    // Excel에서 한글 CSV를 열 때 사용할 UTF-8 BOM.
                    output.write(new byte[] {
                            (byte) 0xEF, (byte) 0xBB, (byte) 0xBF
                    });

                    long writtenBytes = 3;
                    List<String> values = new ArrayList<>(columnCount);

                    // 컬럼 별칭이 있으면 별칭을 제목으로 사용한다.
                    for (int column = 1; column <= columnCount; column++) {
                        values.add(metadata.getColumnLabel(column));
                    }

                    writtenBytes = writeCsvRow(output, values, writtenBytes);

                    long rowCount = 0;

                    while (resultSet.next()) {
                        values.clear();

                        for (int column = 1; column <= columnCount; column++) {
                            values.add(resultSet.getString(column));
                        }

                        writtenBytes = writeCsvRow(output, values, writtenBytes);
                        rowCount++;
                    }

                    return rowCount;
                }
            }
        }
    }

    /**
     * 모든 값을 큰따옴표로 감싸 쉼표와 줄바꿈을 보존한다.
     * 값 안의 큰따옴표는 두 번 기록하고, SQL NULL은 빈 값으로 기록한다.
     */
    private long writeCsvRow(
            OutputStream output, List<String> values, long writtenBytes)
            throws IOException {

        StringBuilder row = new StringBuilder();

        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                row.append(',');
            }

            String value = values.get(index);

            row.append('"');

            if (value != null) {
                row.append(value.replace("\"", "\"\""));
            }

            row.append('"');
        }

        row.append("\r\n");

        byte[] bytes = row.toString().getBytes(StandardCharsets.UTF_8);

        if (bytes.length > maxFileBytes - writtenBytes) {
            throw new IOException("조회 결과 파일의 최대 크기를 초과했습니다.");
        }

        output.write(bytes);

        return writtenBytes + bytes.length;
    }

    /** 파일 정리 실패가 원래 SQL·저장 오류를 덮어쓰지 않도록 한다. */
    private void deleteIncompleteFile(Path resultFile, Exception originalException) {
        try {
            Files.deleteIfExists(resultFile);
        } catch (IOException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }

    /**
     * 결과 저장소 기준 상대 경로와 실행 결과 정보를 전달한다.
     * rowCount는 제목 행을 제외한 데이터 행 수다.
     */
    public record ExecutionResult(
            String relativePath,
            long rowCount,
            long fileSize,
            long durationMs) {
    }
}