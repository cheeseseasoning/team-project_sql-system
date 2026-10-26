package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.mapper.ExecutionResultMapper;
import com.sqlflow.execution.service.ExecutionResultAccessService;
import com.sqlflow.execution.service.ExecutionResultService;

@ExtendWith(MockitoExtension.class)
class ExecutionResultServiceTest {

    private static final Long ACTOR_ID = 10L;
    private static final Long EXECUTION_ID = 20L;
    private static final Long QUERY_ID = 30L;

    @Mock
    private ExecutionResultMapper executionResultMapper;

    @Mock
    private ExecutionResultAccessService executionResultAccessService;

    @InjectMocks
    private ExecutionResultService service;

    @Test
    void previewUsesOnlyPreviewPermissionCheck() {
        ExecutionResultFile file = prepareAvailableFile();

        ExecutionResultFile result =
                service.getMyResultFile(EXECUTION_ID, ACTOR_ID);

        assertSame(file, result);

        verify(executionResultAccessService)
                .requirePreview(ACTOR_ID, QUERY_ID);

        // 다운로드용 반출 검사를 호출하지 않았는지도 확인한다.
        verifyNoMoreInteractions(executionResultAccessService);
    }

    @Test
    void downloadUsesOnlyDownloadPermissionCheck() {
        ExecutionResultFile file = prepareAvailableFile();

        ExecutionResultFile result =
                service.getMyDownloadFile(EXECUTION_ID, ACTOR_ID);

        assertSame(file, result);

        verify(executionResultAccessService)
                .requireDownload(ACTOR_ID, QUERY_ID);

        verifyNoMoreInteractions(executionResultAccessService);
    }

    @Test
    void missingLoginRejectsBothOperationsBeforeDatabaseAccess() {
        assertRejected(
                HttpStatus.UNAUTHORIZED,
                () -> service.getMyResultFile(EXECUTION_ID, null));

        assertRejected(
                HttpStatus.UNAUTHORIZED,
                () -> service.getMyDownloadFile(EXECUTION_ID, null));

        verifyNoInteractions(
                executionResultMapper,
                executionResultAccessService);
    }

    @Test
    void invalidExecutionIdRejectsBothOperationsBeforeDatabaseAccess() {
        assertRejected(
                HttpStatus.BAD_REQUEST,
                () -> service.getMyResultFile(0L, ACTOR_ID));

        assertRejected(
                HttpStatus.BAD_REQUEST,
                () -> service.getMyDownloadFile(0L, ACTOR_ID));

        verifyNoInteractions(
                executionResultMapper,
                executionResultAccessService);
    }

    @Test
    void missingOrInaccessibleExecutionRejectsBothOperations() {
        when(executionResultMapper.selectMyResultFile(
                EXECUTION_ID, ACTOR_ID))
                .thenReturn(null);

        assertBothRejected(HttpStatus.NOT_FOUND);
    }

    @Test
    void pendingExecutionRejectsBothOperations() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setStatus("PENDING");

        assertBothRejected(HttpStatus.CONFLICT);
    }

    @Test
    void runningExecutionRejectsBothOperations() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setStatus("RUNNING");

        assertBothRejected(HttpStatus.CONFLICT);
    }

    @Test
    void failedExecutionRejectsBothOperations() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setStatus("FAILED");

        assertBothRejected(HttpStatus.CONFLICT);
    }

    @Test
    void missingResultMetadataRejectsBothOperations() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setResultId(null);

        assertBothRejected(HttpStatus.NOT_FOUND);
    }

    @Test
    void deletedResultRejectsBothOperationsBeforePermissionCheck() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setDeletedAt(now().minusMinutes(1));

        assertBothRejected(HttpStatus.GONE);
    }

    @Test
    void expiredResultRejectsBothOperationsBeforePermissionCheck() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setExpiresAt(now().minusMinutes(1));

        assertBothRejected(HttpStatus.GONE);
    }

    @Test
    void missingFilePathRejectsBothOperations() {
        ExecutionResultFile file = prepareAvailableFile();
        file.setFilePath(" ");

        assertBothRejected(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void previewPermissionDenialIsPropagated() {
        prepareAvailableFile();

        ResponseStatusException denied =
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "민감 데이터 조회 권한이 없습니다.");

        doThrow(denied)
                .when(executionResultAccessService)
                .requirePreview(ACTOR_ID, QUERY_ID);

        ResponseStatusException actual = assertThrows(
                ResponseStatusException.class,
                () -> service.getMyResultFile(
                        EXECUTION_ID, ACTOR_ID));

        assertSame(denied, actual);

        verify(executionResultAccessService)
                .requirePreview(ACTOR_ID, QUERY_ID);

        verifyNoMoreInteractions(executionResultAccessService);
    }

    @Test
    void downloadDenialDoesNotPreventSubsequentPreview() {
        ExecutionResultFile file = prepareAvailableFile();

        ResponseStatusException denied =
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "CSV 다운로드가 제한된 결과입니다.");

        doThrow(denied)
                .when(executionResultAccessService)
                .requireDownload(ACTOR_ID, QUERY_ID);

        ResponseStatusException actual = assertThrows(
                ResponseStatusException.class,
                () -> service.getMyDownloadFile(
                        EXECUTION_ID, ACTOR_ID));

        assertSame(denied, actual);

        // 다운로드 거부 이후에도 미리보기는 별도 검사로 제공한다.
        ExecutionResultFile preview =
                service.getMyResultFile(EXECUTION_ID, ACTOR_ID);

        assertSame(file, preview);

        verify(executionResultAccessService)
                .requireDownload(ACTOR_ID, QUERY_ID);

        verify(executionResultAccessService)
                .requirePreview(ACTOR_ID, QUERY_ID);

        verifyNoMoreInteractions(executionResultAccessService);
    }

    private ExecutionResultFile prepareAvailableFile() {
        ExecutionResultFile file = new ExecutionResultFile();

        file.setExecutionId(EXECUTION_ID);
        file.setQueryId(QUERY_ID);
        file.setResultId(40L);
        file.setStatus("SUCCESS");
        file.setFilePath("test-result.csv");
        file.setExpiresAt(now().plusDays(1));
        file.setDeletedAt(null);

        when(executionResultMapper.selectMyResultFile(
                EXECUTION_ID, ACTOR_ID))
                .thenReturn(file);

        return file;
    }

    private void assertBothRejected(HttpStatus expectedStatus) {
        assertRejected(
                expectedStatus,
                () -> service.getMyResultFile(
                        EXECUTION_ID, ACTOR_ID));

        assertRejected(
                expectedStatus,
                () -> service.getMyDownloadFile(
                        EXECUTION_ID, ACTOR_ID));

        // 파일 상태에서 차단되면 권한 서비스까지 진행하면 안 된다.
        verifyNoInteractions(executionResultAccessService);
    }

    private void assertRejected(
            HttpStatus expectedStatus,
            Executable action) {

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                action);

        assertEquals(expectedStatus, error.getStatusCode());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}