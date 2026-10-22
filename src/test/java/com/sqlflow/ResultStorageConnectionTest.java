package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ResultStorageConnectionTest {

    @Value("${sqlflow.result.storage-root}")
    private String storageRoot;

    @Test
    void canWriteAndReadSharedFolder() throws Exception {
        Path root = Path.of(storageRoot);

        assertTrue(Files.isDirectory(root),
                "공유 폴더가 없거나 접근할 수 없습니다: " + root);

        // 팀원이 동시에 테스트해도 파일명이 겹치지 않도록 생성
        Path testFile = Files.createTempFile(root, "sqlflow-test-", ".txt");

        try {
            String content = "공유 폴더 한글 저장 테스트";

            Files.writeString(testFile, content, StandardCharsets.UTF_8);

            assertEquals(
                    content,
                    Files.readString(testFile, StandardCharsets.UTF_8)
            );
        } finally {
            Files.deleteIfExists(testFile);
        }
    }
}