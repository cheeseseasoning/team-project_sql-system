package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import com.sqlflow.execution.service.MySqlQueryExecutionService;
import com.sqlflow.execution.service.ResultFileStorageService;

/**
 * 실제 MySQL 조회 전용 연결로 SQL을 실행한다.
 * 업무 데이터는 수정하지 않고 결과 파일은 임시 폴더에 저장한다.
 */
@SpringBootTest(properties = {
		"sqlflow.result.storage-root=${java.io.tmpdir}/sqlflow-test-results",
        "app.execution.query-timeout-seconds=60",
        "app.execution.max-file-bytes=104857600"
})
class MySqlQueryExecutionIntegrationTest {

    @Autowired
    @Qualifier("queryDataSource")
    private DataSource queryDataSource;

    @TempDir
    Path resultDirectory;

    private MySqlQueryExecutionService service;

    @BeforeEach
    void setUp() {
        // 공통 저장소가 아닌 테스트 전용 임시 폴더를 사용한다.
        service = new MySqlQueryExecutionService(
                queryDataSource,
                resultDirectory.toString(),
                60,
                104_857_600,
                new ResultFileStorageService(resultDirectory.toString()));
    }

    @Test
    void readerConnectionExecutesQueryAndSavesCsv() throws Exception {
        // 실제 사용하는 DB와 인증 계정을 확인한다.
        try (var connection = queryDataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery(
                     "SELECT DATABASE(), CURRENT_USER()")) {

            assertEquals(
                    "MySQL",
                    connection.getMetaData().getDatabaseProductName());

            assertTrue(resultSet.next());
            assertEquals("sqlflow_demo", resultSet.getString(1));
            assertTrue(
                    resultSet.getString(2).startsWith("sqlflow_reader@"),
                    "업무 조회에는 sqlflow_reader 계정을 사용해야 합니다.");
        }

        String sql = """
                SELECT department_id, department_name
                FROM demo_department
                ORDER BY department_id
                """;

        var result = service.execute(sql);

        Path file = resultDirectory.resolve(result.relativePath());
        String csv = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(Files.isRegularFile(file));
        assertTrue(result.rowCount() > 0, "시연 부서 데이터가 필요합니다.");
        assertEquals(Files.size(file), result.fileSize());
        assertTrue(csv.startsWith(
                "\uFEFF\"department_id\",\"department_name\"\r\n"));
        assertFalse(Path.of(result.relativePath()).isAbsolute());
    }

    @Test
    void zeroRowsStillCreatesHeaderOnlyCsv() throws Exception {
        String sql = """
                SELECT department_id, department_name
                FROM demo_department
                WHERE 1 = 0
                """;

        var result = service.execute(sql);

        String csv = Files.readString(
                resultDirectory.resolve(result.relativePath()),
                StandardCharsets.UTF_8);

        assertEquals(0L, result.rowCount());
        assertEquals(
                "\uFEFF\"department_id\",\"department_name\"\r\n",
                csv);
    }
}