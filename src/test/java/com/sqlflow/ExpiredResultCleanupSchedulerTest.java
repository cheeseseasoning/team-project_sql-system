package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sqlflow.config.ResultCleanupSchedulingConfig;
import com.sqlflow.execution.scheduler.ExpiredResultCleanupScheduler;
import com.sqlflow.execution.service.ExpiredResultCleanupBatchService;
import com.sqlflow.execution.service.ExpiredResultCleanupBatchService.CleanupSummary;

@ExtendWith(MockitoExtension.class)
class ExpiredResultCleanupSchedulerTest {

    @Mock
    private ExpiredResultCleanupBatchService batchService;

    @Test
    void scheduledRunCallsBatchService() {
        when(batchService.cleanupExpiredResults(100))
                .thenReturn(summary());

        var scheduler = scheduler(false);
        scheduler.runScheduledCleanup();

        verify(batchService).cleanupExpiredResults(100);
    }

    @Test
    void startupDoesNotRunWhenDisabled() {
        scheduler(false).onApplicationReady();

        verifyNoInteractions(batchService);
    }

    @Test
    void startupRunsWhenEnabled() {
        when(batchService.cleanupExpiredResults(100))
                .thenReturn(summary());

        scheduler(true).onApplicationReady();

        verify(batchService).cleanupExpiredResults(100);
    }

    @Test
    void failureDoesNotPreventNextRun() {
        when(batchService.cleanupExpiredResults(100))
                .thenThrow(new IllegalStateException("DB 연결 실패"))
                .thenReturn(summary());

        var scheduler = scheduler(false);

        assertDoesNotThrow(scheduler::runScheduledCleanup);
        assertDoesNotThrow(scheduler::runScheduledCleanup);

        verify(batchService, times(2)).cleanupExpiredResults(100);
    }

    @Test
    void overlappingRunIsSkipped() throws Exception {
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);

        when(batchService.cleanupExpiredResults(100))
                .thenAnswer(invocation -> {
                    started.countDown();

                    if (!finish.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                                "테스트 종료 신호 대기 시간 초과");
                    }

                    return summary();
                });

        var scheduler = scheduler(false);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(scheduler::runScheduledCleanup);

            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));

                // 첫 실행이 끝나지 않았으므로 두 번째 호출은 건너뛴다.
                scheduler.runScheduledCleanup();

                verify(batchService, times(1))
                        .cleanupExpiredResults(100);

            } finally {
                finish.countDown();
            }

            first.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void configurationRegistersSchedulerOnlyWhenEnabled() {
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(ResultCleanupSchedulingConfig.class)
                .withBean(
                        ExpiredResultCleanupBatchService.class,
                        () -> batchService);

        runner.withPropertyValues(
                "sqlflow.result.cleanup.enabled=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());

                    assertEquals(
                            0,
                            context.getBeansOfType(
                                    ExpiredResultCleanupScheduler.class)
                                    .size());
                });

        runner.withPropertyValues(
                "sqlflow.result.cleanup.enabled=true",
                // 설정 등록만 검증하고 예약 작업은 실행하지 않는다.
                "sqlflow.result.cleanup.cron=-",
                "sqlflow.result.cleanup.run-on-startup=false")
                .run(context -> {
                    assertNull(context.getStartupFailure());

                    assertEquals(
                            1,
                            context.getBeansOfType(
                                    ExpiredResultCleanupScheduler.class)
                                    .size());
                });

        verifyNoInteractions(batchService);
    }

    private ExpiredResultCleanupScheduler scheduler(boolean runOnStartup) {
        return new ExpiredResultCleanupScheduler(
                batchService,
                100,
                runOnStartup);
    }

    private CleanupSummary summary() {
        return new CleanupSummary(
                LocalDateTime.of(2026, 10, 27, 18, 0),
                2,
                1,
                0,
                0);
    }
}