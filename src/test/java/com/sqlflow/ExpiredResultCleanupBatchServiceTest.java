package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sqlflow.execution.domain.ExpiredResultFile;
import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.service.ExpiredResultCleanupBatchService;
import com.sqlflow.execution.service.ExpiredResultCleanupService;
import com.sqlflow.execution.service.ExpiredResultCleanupService.CleanupResult;

@ExtendWith(MockitoExtension.class)
class ExpiredResultCleanupBatchServiceTest {

    private static final LocalDateTime CUTOFF =
            LocalDateTime.of(2026, 10, 27, 12, 0);

    @Mock
    private QueryResultMapper mapper;

    @Mock
    private ExpiredResultCleanupService cleanupService;

    @InjectMocks
    private ExpiredResultCleanupBatchService service;

    @Test
    void emptyTargetsDoNotCallCleanup() {
        when(mapper.selectCleanupCutoff()).thenReturn(CUTOFF);
        when(mapper.selectExpiredResults(0, CUTOFF, 2))
                .thenReturn(List.of());

        var summary = service.cleanupExpiredResults(2);

        assertEquals(CUTOFF, summary.cutoff());
        assertEquals(0L, summary.attempted());
        verifyNoInteractions(cleanupService);
    }

    @Test
    void processesMultiplePagesWithSameCutoff() throws Exception {
        when(mapper.selectCleanupCutoff()).thenReturn(CUTOFF);

        when(mapper.selectExpiredResults(0, CUTOFF, 2))
                .thenReturn(List.of(target(1), target(2)));

        when(mapper.selectExpiredResults(2, CUTOFF, 2))
                .thenReturn(List.of(target(3)));

        when(mapper.selectExpiredResults(3, CUTOFF, 2))
                .thenReturn(List.of());

        when(cleanupService.cleanupOne(1L, path(1), CUTOFF))
                .thenReturn(CleanupResult.DELETED);

        when(cleanupService.cleanupOne(2L, path(2), CUTOFF))
                .thenReturn(CleanupResult.ALREADY_MISSING);

        when(cleanupService.cleanupOne(3L, path(3), CUTOFF))
                .thenReturn(CleanupResult.SKIPPED);

        var summary = service.cleanupExpiredResults(2);

        assertEquals(3L, summary.attempted());
        assertEquals(1L, summary.deleted());
        assertEquals(1L, summary.missing());
        assertEquals(1L, summary.skipped());
        assertEquals(0L, summary.failed());

        verify(mapper, times(1)).selectCleanupCutoff();
    }

    @Test
    void failureDoesNotPreventNextPage() throws Exception {
        when(mapper.selectCleanupCutoff()).thenReturn(CUTOFF);

        when(mapper.selectExpiredResults(0, CUTOFF, 1))
                .thenReturn(List.of(target(1)));

        when(mapper.selectExpiredResults(1, CUTOFF, 1))
                .thenReturn(List.of(target(2)));

        when(mapper.selectExpiredResults(2, CUTOFF, 1))
                .thenReturn(List.of());

        when(cleanupService.cleanupOne(1L, path(1), CUTOFF))
                .thenThrow(new IOException("파일 사용 중"));

        when(cleanupService.cleanupOne(2L, path(2), CUTOFF))
                .thenReturn(CleanupResult.DELETED);

        var summary = service.cleanupExpiredResults(1);

        assertEquals(2L, summary.attempted());
        assertEquals(1L, summary.failed());
        assertEquals(1L, summary.deleted());

        verify(cleanupService).cleanupOne(2L, path(2), CUTOFF);
        verify(mapper).selectExpiredResults(1, CUTOFF, 1);
    }

    @Test
    void failedTargetIsRetriedOnNextRun() throws Exception {
        when(mapper.selectCleanupCutoff()).thenReturn(CUTOFF);

        when(mapper.selectExpiredResults(0, CUTOFF, 1))
                .thenReturn(List.of(target(1)));

        when(mapper.selectExpiredResults(1, CUTOFF, 1))
                .thenReturn(List.of());

        when(cleanupService.cleanupOne(1L, path(1), CUTOFF))
                .thenThrow(new IOException("일시적인 삭제 실패"))
                .thenReturn(CleanupResult.DELETED);

        var first = service.cleanupExpiredResults(1);
        var second = service.cleanupExpiredResults(1);

        assertEquals(1L, first.failed());
        assertEquals(0L, first.deleted());

        assertEquals(0L, second.failed());
        assertEquals(1L, second.deleted());

        verify(mapper, times(2)).selectExpiredResults(0, CUTOFF, 1);
        verify(cleanupService, times(2))
                .cleanupOne(1L, path(1), CUTOFF);
    }

    @Test
    void targetQueryFailureIsPropagated() {
        when(mapper.selectCleanupCutoff()).thenReturn(CUTOFF);

        var failure = new IllegalStateException("대상 조회 실패");

        when(mapper.selectExpiredResults(0, CUTOFF, 2))
                .thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.cleanupExpiredResults(2)));

        verifyNoInteractions(cleanupService);
    }

    @Test
    void invalidBatchSizeIsRejectedBeforeDatabaseAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.cleanupExpiredResults(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.cleanupExpiredResults(1001));

        verifyNoInteractions(mapper, cleanupService);
    }

    private ExpiredResultFile target(long id) {
        var target = new ExpiredResultFile();
        target.setResultId(id);
        target.setFilePath(path(id));
        return target;
    }

    private String path(long id) {
        return "query-" + id + ".csv";
    }
}