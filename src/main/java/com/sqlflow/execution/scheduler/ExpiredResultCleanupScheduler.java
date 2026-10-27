package com.sqlflow.execution.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import com.sqlflow.execution.service.ExpiredResultCleanupBatchService;

/** 만료 CSV의 정기 정리를 호출한다. */
public class ExpiredResultCleanupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(ExpiredResultCleanupScheduler.class);

    private final ExpiredResultCleanupBatchService batchService;
    private final int batchSize;
    private final boolean runOnStartup;

    private final AtomicBoolean running = new AtomicBoolean();

    public ExpiredResultCleanupScheduler(
            ExpiredResultCleanupBatchService batchService,
            int batchSize,
            boolean runOnStartup) {

        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException(
                    "정리 조회 크기는 1~1000이어야 합니다.");
        }

        this.batchService = batchService;
        this.batchSize = batchSize;
        this.runOnStartup = runOnStartup;
    }

    @Scheduled(
            cron = "${sqlflow.result.cleanup.cron:0 0 18 * * *}",
            zone = "Asia/Seoul")
    public void runScheduledCleanup() {
        runCleanup("SCHEDULED");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (runOnStartup) {
            runCleanup("STARTUP");
        }
    }

    private void runCleanup(String trigger) {
        // 같은 애플리케이션에서 시작 시 실행과 정기 실행이 겹치지 않게 한다.
        if (!running.compareAndSet(false, true)) {
            log.warn("만료 CSV 정리 건너뜀: 이미 실행 중, trigger={}", trigger);
            return;
        }

        try {
            var summary = batchService.cleanupExpiredResults(batchSize);

            log.info(
                    "만료 CSV 정리 완료: trigger={}, cutoff={}, "
                            + "attempted={}, deleted={}, missing={}, "
                            + "skipped={}, failed={}",
                    trigger,
                    summary.cutoff(),
                    summary.attempted(),
                    summary.deleted(),
                    summary.missing(),
                    summary.skipped(),
                    summary.failed());

        } catch (RuntimeException exception) {
            // 대상 조회·DB 장애 등 전체 실행 실패를 기록한다.
            // 다음 예약 실행은 계속 가능해야 한다.
            log.error(
                    "만료 CSV 정리 실행 실패: trigger={}",
                    trigger,
                    exception);

        } finally {
            running.set(false);
        }
    }
}