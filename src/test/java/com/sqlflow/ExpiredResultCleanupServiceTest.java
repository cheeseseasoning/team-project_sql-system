package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sqlflow.execution.domain.ExpiredResultFile;
import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.service.ExpiredResultCleanupService;
import com.sqlflow.execution.service.ExpiredResultCleanupService.CleanupResult;
import com.sqlflow.execution.service.ResultFileStorageService;

@ExtendWith(MockitoExtension.class)
class ExpiredResultCleanupServiceTest {

    private static final Long RESULT_ID = 100L;
    private static final String FILE_PATH = "query-test.csv";
    private static final LocalDateTime CUTOFF =
            LocalDateTime.of(2026, 10, 27, 12, 0);

    @Mock
    private QueryResultMapper mapper;

    @Mock
    private ResultFileStorageService storage;

    @InjectMocks
    private ExpiredResultCleanupService service;

    @Test
    void deletesFileBeforeMarkingDeleted() throws Exception {
        prepareTarget();

        when(storage.deleteIfExists(FILE_PATH)).thenReturn(true);
        when(mapper.markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF)).thenReturn(1);

        assertEquals(
                CleanupResult.DELETED,
                service.cleanupOne(RESULT_ID, FILE_PATH, CUTOFF));

        var order = inOrder(mapper, storage);

        order.verify(mapper).selectExpiredResultForUpdate(
                RESULT_ID, FILE_PATH, CUTOFF);

        order.verify(storage).deleteIfExists(FILE_PATH);

        order.verify(mapper).markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF);
    }

    @Test
    void missingFileStillMarksDeleted() throws Exception {
        prepareTarget();

        when(storage.deleteIfExists(FILE_PATH)).thenReturn(false);
        when(mapper.markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF)).thenReturn(1);

        assertEquals(
                CleanupResult.ALREADY_MISSING,
                service.cleanupOne(RESULT_ID, FILE_PATH, CUTOFF));

        verify(mapper).markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF);
    }

    @Test
    void changedOrAlreadyProcessedTargetIsSkipped() throws Exception {
        // 잠금 조회가 null이면 파일에 접근하지 않는다.
        assertEquals(
                CleanupResult.SKIPPED,
                service.cleanupOne(RESULT_ID, FILE_PATH, CUTOFF));

        verifyNoInteractions(storage);

        verify(mapper, never()).markExpiredResultDeleted(
                anyLong(), anyString(), any());
    }

    @Test
    void deletionFailureDoesNotMarkDeleted() throws Exception {
        prepareTarget();

        var failure = new IOException("파일 삭제 실패");

        when(storage.deleteIfExists(FILE_PATH)).thenThrow(failure);

        assertSame(
                failure,
                assertThrows(
                        IOException.class,
                        () -> service.cleanupOne(
                                RESULT_ID, FILE_PATH, CUTOFF)));

        verify(mapper, never()).markExpiredResultDeleted(
                anyLong(), anyString(), any());
    }

    @Test
    void databaseFailureCanBeRetriedAfterPhysicalDeletion()
            throws Exception {

        prepareTarget();

        // 첫 호출에서는 파일 삭제, 재호출에서는 이미 없음.
        when(storage.deleteIfExists(FILE_PATH))
                .thenReturn(true, false);

        var failure = new IllegalStateException("DB 기록 실패");

        when(mapper.markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF))
                .thenThrow(failure)
                .thenReturn(1);

        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () -> service.cleanupOne(
                                RESULT_ID, FILE_PATH, CUTOFF)));

        assertEquals(
                CleanupResult.ALREADY_MISSING,
                service.cleanupOne(RESULT_ID, FILE_PATH, CUTOFF));

        verify(storage, times(2)).deleteIfExists(FILE_PATH);
    }

    @Test
    void unexpectedUpdateCountIsAnError() throws Exception {
        prepareTarget();

        when(storage.deleteIfExists(FILE_PATH)).thenReturn(true);
        when(mapper.markExpiredResultDeleted(
                RESULT_ID, FILE_PATH, CUTOFF)).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.cleanupOne(
                        RESULT_ID, FILE_PATH, CUTOFF));
    }

    private void prepareTarget() {
        ExpiredResultFile target = new ExpiredResultFile();
        target.setResultId(RESULT_ID);
        target.setFilePath(FILE_PATH);

        when(mapper.selectExpiredResultForUpdate(
                RESULT_ID, FILE_PATH, CUTOFF))
                .thenReturn(target);
    }
}