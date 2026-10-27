package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import com.sqlflow.execution.service.ResultFileStorageService;

class ResultFileStorageServiceTest {

    @TempDir
    Path temporaryDirectory;

    private Path storageRoot;
    private ResultFileStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        storageRoot = Files.createDirectory(
                temporaryDirectory.resolve("results"));

        service = new ResultFileStorageService(storageRoot.toString());
    }

    @Test
    void deletesCsvWithoutDeletingOtherFiles() throws IOException {
        Path target = Files.writeString(
                storageRoot.resolve("query-target.csv"), "id\r\n1\r\n");

        Path other = Files.writeString(
                storageRoot.resolve("query-other.csv"), "id\r\n2\r\n");

        assertTrue(service.deleteIfExists("query-target.csv"));

        assertFalse(Files.exists(target));
        assertEquals("id\r\n2\r\n", Files.readString(other));
    }

    @Test
    void repeatedDeletionIsSafe() throws IOException {
        Files.writeString(storageRoot.resolve("query-test.csv"), "id");

        assertTrue(service.deleteIfExists("query-test.csv"));
        assertFalse(service.deleteIfExists("query-test.csv"));
    }

    @Test
    void missingFileReturnsFalse() throws IOException {
        assertFalse(service.deleteIfExists("query-missing.csv"));
    }

    @Test
    void rejectsTraversalAndAbsolutePathWithoutDeletingOutsideFile()
            throws IOException {

        Path outside = Files.writeString(
                temporaryDirectory.resolve("outside.csv"), "keep");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists("../outside.csv"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists(outside.toString()));

        assertEquals("keep", Files.readString(outside));
    }

    @Test
    void rejectsNestedPathWithoutDeletingFile() throws IOException {
        Path nested = Files.createDirectory(storageRoot.resolve("nested"));
        Path file = Files.writeString(nested.resolve("query.csv"), "keep");

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists("nested/query.csv"));

        assertEquals("keep", Files.readString(file));
    }

    @Test
    void rejectsBlankAndNonCsvNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists(null));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists(" "));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deleteIfExists("notes.txt"));
    }

    @Test
    void rejectsDirectoryEvenWhenNameEndsWithCsv() throws IOException {
        Path directory = Files.createDirectory(
                storageRoot.resolve("directory.csv"));

        assertThrows(
                IOException.class,
                () -> service.deleteIfExists("directory.csv"));

        assertTrue(Files.isDirectory(directory));
    }

    @Test
    void missingStorageRootIsAnError() throws IOException {
        // 비어 있는 테스트 전용 저장소만 삭제한다.
        Files.delete(storageRoot);

        assertThrows(
                IOException.class,
                () -> service.deleteIfExists("query-missing.csv"));
    }

    @Test
    void rejectsSymbolicLinkWithoutDeletingTarget() throws IOException {
        Path outside = Files.writeString(
                temporaryDirectory.resolve("outside.csv"), "keep");

        Path link = storageRoot.resolve("query-link.csv");

        try {
            Files.createSymbolicLink(link, outside);

        } catch (IOException
                | UnsupportedOperationException
                | SecurityException exception) {

            Assumptions.assumeTrue(
                    false,
                    "심볼릭 링크 생성이 허용되지 않는 환경: "
                            + exception.getMessage());
            return;
        }

        assertThrows(
                IOException.class,
                () -> service.deleteIfExists("query-link.csv"));

        assertTrue(Files.isSymbolicLink(link));
        assertEquals("keep", Files.readString(outside));
    }
}