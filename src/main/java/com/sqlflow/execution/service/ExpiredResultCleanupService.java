package com.sqlflow.execution.service;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.execution.domain.ExpiredResultFile;
import com.sqlflow.execution.mapper.QueryResultMapper;

import lombok.RequiredArgsConstructor;

/**
 * 만료 결과 한 건의 파일 삭제와 삭제 완료 기록을 담당한다.
 *
 * 파일 삭제는 DB 롤백으로 복구되지 않는다.
 * 파일 삭제 후 DB 기록에 실패하면 다음 호출에서 재처리한다.
 */
@Service
@RequiredArgsConstructor
public class ExpiredResultCleanupService {

    private final QueryResultMapper queryResultMapper;
    private final ResultFileStorageService resultFileStorageService;

    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.REQUIRES_NEW,
            rollbackFor = IOException.class)
    public CleanupResult cleanupOne(
            Long resultId,
            String expectedFilePath,
            LocalDateTime cutoff) throws IOException {

        if (resultId == null || resultId <= 0) {
            throw new IllegalArgumentException(
                    "올바른 결과 ID가 필요합니다.");
        }

        if (expectedFilePath == null || expectedFilePath.isBlank()) {
            throw new IllegalArgumentException(
                    "정리 대상 파일 경로가 필요합니다.");
        }

        if (cutoff == null) {
            throw new IllegalArgumentException(
                    "만료 기준 시각이 필요합니다.");
        }

        ExpiredResultFile target =
                queryResultMapper.selectExpiredResultForUpdate(
                        resultId,
                        expectedFilePath,
                        cutoff);

        if (target == null) {
            // 이미 삭제됐거나 만료·경로 조건이 달라진 경우.
            return CleanupResult.SKIPPED;
        }

        boolean deleted = resultFileStorageService.deleteIfExists(
                target.getFilePath());

        int updated = queryResultMapper.markExpiredResultDeleted(
                target.getResultId(),
                target.getFilePath(),
                cutoff);

        if (updated != 1) {
            throw new IllegalStateException(
                    "삭제 완료 상태가 정확히 한 건 갱신되지 않았습니다. "
                            + "resultId=" + resultId);
        }

        return deleted
                ? CleanupResult.DELETED
                : CleanupResult.ALREADY_MISSING;
    }

    public enum CleanupResult {
        DELETED,
        ALREADY_MISSING,
        SKIPPED
    }
}