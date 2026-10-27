package com.sqlflow.execution.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 결과 저장소의 CSV 파일 삭제를 담당한다.
 *
 * 현재 생성 방식에 맞춰 저장 루트 바로 아래의 CSV만 허용한다.
 * 저장소 자체에 접근할 수 없는 경우는 파일 없음으로 처리하지 않는다.
 */
@Service
public class ResultFileStorageService {

    private final Path storageRoot;

    public ResultFileStorageService(
            @Value("${sqlflow.result.storage-root}") String storageRoot) {

        this.storageRoot = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * @return 실제 삭제했으면 true, 이미 파일이 없으면 false
     *
     * 잘못된 경로와 삭제 오류는 예외로 전달한다.
     * 호출자는 예외 발생 시 삭제 완료로 기록하면 안 된다.
     */
    public boolean deleteIfExists(String relativePath) throws IOException {
        Path relative = validateFileName(relativePath);

        // 이 단계의 실패는 "개별 파일 없음"으로 취급하지 않는다.
        Path root = storageRoot.toRealPath();

        if (!Files.isDirectory(root)) {
            throw new IOException("결과 저장소가 디렉터리가 아닙니다.");
        }

        Path file = root.resolve(relative).normalize();

        if (!root.equals(file.getParent())) {
            throw new IllegalArgumentException(
                    "저장 루트 바로 아래의 CSV만 삭제할 수 있습니다.");
        }

        BasicFileAttributes attributes;

        try {
            attributes = Files.readAttributes(
                    file,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);

        } catch (NoSuchFileException exception) {
            // 확인 중 저장소 자체가 사라진 경우와 구분한다.
            root.toRealPath();
            return false;
        }

        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new IOException(
                    "일반 CSV 파일만 삭제할 수 있습니다.");
        }

        boolean deleted = Files.deleteIfExists(file);

        if (!deleted) {
            // 다른 작업이 먼저 삭제한 경우도 재처리 가능하다.
            root.toRealPath();
        }

        return deleted;
    }

    private Path validateFileName(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException(
                    "삭제할 결과 파일명이 필요합니다.");
        }

        Path relative = Path.of(relativePath);

        if (relative.isAbsolute()
                || relative.getRoot() != null
                || relative.getNameCount() != 1
                || !relative.equals(relative.getFileName())
                || !relativePath.endsWith(".csv")) {

            throw new IllegalArgumentException(
                    "하위 경로 없는 CSV 파일명만 사용할 수 있습니다.");
        }

        return relative;
    }
}