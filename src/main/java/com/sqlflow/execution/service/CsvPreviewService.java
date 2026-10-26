package com.sqlflow.execution.service;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.dto.ExecutionResultPreviewResponse;

@Service
public class CsvPreviewService {

    // 전체 미리보기 한도와 요청당 행 수 제한은 별도로 관리한다.
    private static final int MAX_PREVIEW_ROWS = 100;
    private static final int MAX_PAGE_SIZE = 100;

    private final Path storageRoot;

    public CsvPreviewService(
            @Value("${sqlflow.result.storage-root}") String storageRoot) {

        this.storageRoot = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * relativePath: 저장 루트 기준 상대 경로
     * offset: 헤더를 제외하고 건너뛸 데이터 행 수
     * limit: 이번 요청에서 반환할 최대 행 수
     *
     * 실제 결과 연결 시 relativePath는 클라이언트 입력이 아니라
     * 권한 검사 후 QUERY_RESULT.FILE_PATH에서 가져온다.
     */
    public ExecutionResultPreviewResponse read(
            String relativePath,
            long offset,
            int limit) {

        validatePaging(offset, limit);

        try {
            Path file = resolveFile(relativePath);

            try (PushbackReader reader = new PushbackReader(
                    Files.newBufferedReader(file, StandardCharsets.UTF_8),
                    1)) {

                removeBom(reader);

                try (var parser = CSVFormat.RFC4180.parse(reader)) {
                    Iterator<CSVRecord> records = parser.iterator();

                    if (!records.hasNext()) {
                        throw new IOException("CSV 헤더가 없습니다.");
                    }

                    // Map 대신 순서대로 읽어 중복 컬럼명도 보존한다.
                    List<String> columns = toValues(records.next());

                    long skipped = 0;

                    while (skipped < offset && records.hasNext()) {
                        requireColumnCount(records.next(), columns.size());
                        skipped++;
                    }

                    int allowedRows = (int) Math.min(
                            limit,
                            MAX_PREVIEW_ROWS - offset
                    );

                    List<List<String>> rows = new ArrayList<>();

                    while (rows.size() < allowedRows && records.hasNext()) {
                        CSVRecord record = records.next();

                        requireColumnCount(record, columns.size());
                        rows.add(toValues(record));
                    }

                    long nextOffset = skipped + rows.size();

                    boolean hasMore =
                            nextOffset < MAX_PREVIEW_ROWS
                            && records.hasNext();

                    return new ExecutionResultPreviewResponse(
                            columns,
                            List.copyOf(rows),
                            nextOffset,
                            hasMore
                    );
                }
            }

        } catch (NoSuchFileException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장된 CSV 파일을 찾을 수 없습니다.",
                    exception);

        } catch (IOException
                | UncheckedIOException
                | InvalidPathException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CSV 결과 파일을 읽을 수 없습니다.",
                    exception);
        }
    }

    private void validatePaging(long offset, int limit) {
        if (offset < 0 || offset > MAX_PREVIEW_ROWS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "미리보기 시작 위치는 0~100 사이여야 합니다."
            );
        }

        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "한 번에 조회할 행 수는 1~100 사이여야 합니다."
            );
        }
    }

    /**
     * 권한과 만료 여부를 확인한 DB 경로로만 호출한다.
     * 미리보기와 동일한 경로 검사 후 원본 파일을 반환한다.
     */
    public Resource getDownloadResource(String relativePath) {
        try {
            Path file = resolveFile(relativePath);

            if (!Files.isReadable(file)) {
                throw new IOException("결과 파일을 읽을 수 없습니다.");
            }

            return new FileSystemResource(file);

        } catch (NoSuchFileException exception) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장된 CSV 파일을 찾을 수 없습니다.",
                    exception);

        } catch (IOException | InvalidPathException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CSV 파일을 다운로드할 수 없습니다.",
                    exception);
        }
    }
    
    private Path resolveFile(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IOException("결과 파일 경로가 없습니다.");
        }

        Path relative = Path.of(relativePath);

        if (relative.isAbsolute()) {
            throw new IOException("상대 경로만 사용할 수 있습니다.");
        }

        Path root = storageRoot.toRealPath();
        Path candidate = root.resolve(relative).normalize();

        if (!candidate.startsWith(root)) {
            throw new IOException("저장 폴더 밖의 경로입니다.");
        }

        // 심볼릭 링크 등으로 실제 위치가 루트 밖을 가리키는 것도 검사한다.
        Path file = candidate.toRealPath();

        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            throw new IOException("유효한 결과 파일이 아닙니다.");
        }

        return file;
    }

    private void removeBom(PushbackReader reader) throws IOException {
        int first = reader.read();

        // UTF-8 BOM이 있으면 제거하고, 없으면 읽은 문자를 돌려놓는다.
        if (first != -1 && first != '\uFEFF') {
            reader.unread(first);
        }
    }

    private List<String> toValues(CSVRecord record) {
        List<String> values = new ArrayList<>(record.size());

        for (String value : record) {
            values.add(value);
        }

        return List.copyOf(values);
    }

    private void requireColumnCount(
            CSVRecord record,
            int expectedCount) throws IOException {

        if (record.size() != expectedCount) {
            throw new IOException(
                    "CSV 컬럼 수가 일치하지 않습니다. 레코드: "
                    + record.getRecordNumber()
            );
        }
    }
}