package com.sqlflow.execution.service;

import java.io.IOException;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sqlflow.execution.mapper.QueryResultMapper;

import lombok.RequiredArgsConstructor;

/**
 * 만료 결과를 ID 순서로 조회하고 건별 정리 서비스에 전달한다.
 * 한 건의 정리 실패는 기록하고 다음 대상으로 진행한다.
 */
@Service
@RequiredArgsConstructor
public class ExpiredResultCleanupBatchService {

    private static final Logger log =
            LoggerFactory.getLogger(ExpiredResultCleanupBatchService.class);

    private final QueryResultMapper queryResultMapper;
    private final ExpiredResultCleanupService cleanupService;

    /**
     * 전체 작업을 하나의 트랜잭션으로 묶지 않는다.
     * 각 파일은 cleanupService의 별도 트랜잭션에서 처리한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            propagation = Propagation.NEVER)
    public CleanupSummary cleanupExpiredResults(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "정리 조회 크기는 1~1000이어야 합니다.");
        }

        // 실행 도중 기준 시각을 바꾸지 않는다.
        LocalDateTime cutoff =
                queryResultMapper.selectCleanupCutoff();

        if (cutoff == null) {
            throw new IllegalStateException(
                    "DB의 만료 기준 시각을 조회하지 못했습니다.");
        }

        long afterResultId = 0;
        long deleted = 0;
        long missing = 0;
        long skipped = 0;
        long failed = 0;

        while (true) {
            // 조회 자체의 오류는 상위로 전달한다.
            // 정리가 완료된 것으로 잘못 보고하지 않는다.
            var targets = queryResultMapper.selectExpiredResults(
                    afterResultId,
                    cutoff,
                    batchSize);

            if (targets.isEmpty()) {
                break;
            }

            for (var target : targets) {
                if (target == null
                        || target.getResultId() == null
                        || target.getResultId() <= afterResultId) {

                    throw new IllegalStateException(
                            "정리 대상의 결과 ID 순서가 올바르지 않습니다.");
                }

                // 실패한 파일 때문에 같은 페이지를 반복하지 않도록 전진한다.
                afterResultId = target.getResultId();

                try {
                    var result = cleanupService.cleanupOne(
                            target.getResultId(),
                            target.getFilePath(),
                            cutoff);

                    switch (result) {
                        case DELETED -> deleted++;
                        case ALREADY_MISSING -> missing++;
                        case SKIPPED -> skipped++;
                    }

                } catch (IOException | RuntimeException exception) {
                    failed++;

                    log.error(
                            "만료 CSV 정리 실패: resultId={}, filePath={}",
                            target.getResultId(),
                            target.getFilePath(),
                            exception);
                }
            }
        }

        return new CleanupSummary(
                cutoff,
                deleted,
                missing,
                skipped,
                failed);
    }

    public record CleanupSummary(
            LocalDateTime cutoff,
            long deleted,
            long missing,
            long skipped,
            long failed) {

        public long attempted() {
            return deleted + missing + skipped + failed;
        }
    }
}