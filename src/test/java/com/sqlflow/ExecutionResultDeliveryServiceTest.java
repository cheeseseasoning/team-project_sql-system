package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.domain.ResultAccessLogContext;
import com.sqlflow.execution.dto.ExecutionResultPreviewResponse;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;
import com.sqlflow.execution.service.CsvPreviewService;
import com.sqlflow.execution.service.ExecutionResultDeliveryService;
import com.sqlflow.execution.service.ExecutionResultService;
import com.sqlflow.execution.service.ResultAccessLogService;

@ExtendWith(MockitoExtension.class)
class ExecutionResultDeliveryServiceTest {

    private static final Long EXECUTION_ID = 10L;
    private static final Long REQUEST_ID = 20L;
    private static final Long RESULT_ID = 30L;
    private static final Long USER_ID = 40L;
    private static final String FILE_PATH = "test-result.csv";

    @Mock
    private ExecutionResultService executionResultService;

    @Mock
    private CsvPreviewService csvPreviewService;

    @Mock
    private ResultAccessLogMapper resultAccessLogMapper;

    @Mock
    private ResultAccessLogService resultAccessLogService;

    @InjectMocks
    private ExecutionResultDeliveryService service;

    @Test
    void previewReadsDataThenRecordsSuccessBeforeReturning() {
        preparePreviewAccess();

        ExecutionResultPreviewResponse preview = previewResponse();

        when(csvPreviewService.read(FILE_PATH, 0L, 25))
                .thenReturn(preview);

        ExecutionResultPreviewResponse actual =
                service.preview(EXECUTION_ID, USER_ID, 0L, 25);

        assertSame(preview, actual);

        InOrder order = inOrder(
                executionResultService,
                csvPreviewService,
                resultAccessLogService);

        order.verify(executionResultService)
                .getMyResultFile(EXECUTION_ID, USER_ID);

        order.verify(csvPreviewService)
                .read(FILE_PATH, 0L, 25);

        order.verify(resultAccessLogService)
                .record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "PREVIEW", "SUCCESS");

        verifyNoMoreInteractions(resultAccessLogService);
    }

    @Test
    void additionalLoadingRecordsPreviewMore() {
        preparePreviewAccess();

        when(csvPreviewService.read(FILE_PATH, 25L, 25))
                .thenReturn(previewResponse());

        service.preview(EXECUTION_ID, USER_ID, 25L, 25);

        verifyLog("PREVIEW_MORE", "SUCCESS");
    }

    @Test
    void downloadUsesDownloadCheckAndRecordsSuccess() {
        prepareContext();

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        Resource resource = new ByteArrayResource(new byte[] { 1, 2 });

        when(csvPreviewService.getDownloadResource(FILE_PATH))
                .thenReturn(resource);

        assertSame(
                resource,
                service.download(EXECUTION_ID, USER_ID));

        InOrder order = inOrder(
                executionResultService,
                csvPreviewService,
                resultAccessLogService);

        order.verify(executionResultService)
                .getMyDownloadFile(EXECUTION_ID, USER_ID);

        order.verify(csvPreviewService)
                .getDownloadResource(FILE_PATH);

        order.verify(resultAccessLogService)
                .record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "DOWNLOAD", "SUCCESS");

        verifyNoMoreInteractions(
                executionResultService,
                resultAccessLogService);
    }

    @Test
    void previewPermissionDenialRecordsDeniedWithoutReadingCsv() {
        prepareContext();

        ResponseStatusException denied = status(
                HttpStatus.FORBIDDEN,
                "조회 권한이 없습니다.");

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(denied);

        assertSame(
                denied,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.preview(
                                EXECUTION_ID, USER_ID, 0L, 25)));

        verifyLog("PREVIEW", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void expiredDownloadRecordsDeniedWithoutPreparingFile() {
        prepareContext();

        ResponseStatusException expired = status(
                HttpStatus.GONE,
                "보관 기간이 만료되었습니다.");

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(expired);

        assertSame(
                expired,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.download(EXECUTION_ID, USER_ID)));

        verifyLog("DOWNLOAD", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void missingPhysicalFileRecordsErrorRatherThanDenied() {
        prepareContext();

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        ResponseStatusException missingFile = status(
                HttpStatus.NOT_FOUND,
                "저장된 CSV 파일을 찾을 수 없습니다.");

        when(csvPreviewService.getDownloadResource(FILE_PATH))
                .thenThrow(missingFile);

        assertSame(
                missingFile,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.download(EXECUTION_ID, USER_ID)));

        verifyLog("DOWNLOAD", "ERROR");
    }

    @Test
    void invalidPreviewPagingRecordsDenied() {
        preparePreviewAccess();

        ResponseStatusException invalidPaging = status(
                HttpStatus.BAD_REQUEST,
                "미리보기 시작 위치가 올바르지 않습니다.");

        when(csvPreviewService.read(FILE_PATH, -1L, 25))
                .thenThrow(invalidPaging);

        assertSame(
                invalidPaging,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.preview(
                                EXECUTION_ID, USER_ID, -1L, 25)));

        verifyLog("PREVIEW", "DENIED");
    }

    @Test
    void successLogFailureBlocksResponseWithoutSecondLogAttempt() {
        preparePreviewAccess();

        when(csvPreviewService.read(FILE_PATH, 0L, 25))
                .thenReturn(previewResponse());

        ResponseStatusException loggingFailure = status(
                HttpStatus.SERVICE_UNAVAILABLE,
                "접근 로그 저장 실패");

        doThrow(loggingFailure)
                .when(resultAccessLogService)
                .record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "PREVIEW", "SUCCESS");

        assertSame(
                loggingFailure,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.preview(
                                EXECUTION_ID, USER_ID, 0L, 25)));

        // SUCCESS 저장 실패를 ERROR로 다시 기록하면 안 된다.
        verifyLog("PREVIEW", "SUCCESS");
    }

    @Test
    void deniedLogFailureReturns503AndPreservesOriginalDenial() {
        prepareContext();

        ResponseStatusException denied = status(
                HttpStatus.FORBIDDEN,
                "CSV 반출이 차단되었습니다.");

        when(executionResultService.getMyDownloadFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(denied);

        ResponseStatusException loggingFailure = status(
                HttpStatus.SERVICE_UNAVAILABLE,
                "접근 로그 저장 실패");

        doThrow(loggingFailure)
                .when(resultAccessLogService)
                .record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "DOWNLOAD", "DENIED");

        ResponseStatusException actual = assertThrows(
                ResponseStatusException.class,
                () -> service.download(EXECUTION_ID, USER_ID));

        assertSame(loggingFailure, actual);
        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                actual.getStatusCode());

        assertEquals(1, actual.getSuppressed().length);
        assertSame(denied, actual.getSuppressed()[0]);

        verifyLog("DOWNLOAD", "DENIED");
        verifyNoInteractions(csvPreviewService);
    }

    @Test
    void contextLookupFailureBlocksAccessBeforePermissionOrFileRead() {
        when(resultAccessLogMapper.selectAccessContext(
                EXECUTION_ID, USER_ID))
                .thenThrow(
                        new DataAccessResourceFailureException(
                                "테스트용 DB 조회 오류"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.preview(
                        EXECUTION_ID, USER_ID, 0L, 25));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        verifyNoInteractions(
                executionResultService,
                csvPreviewService,
                resultAccessLogService);
    }

    @Test
    void missingContextAndDeniedAccessDoesNotInsertInvalidForeignKeys() {
        when(resultAccessLogMapper.selectAccessContext(
                EXECUTION_ID, USER_ID))
                .thenReturn(null);

        ResponseStatusException missingExecution = status(
                HttpStatus.NOT_FOUND,
                "실행 정보를 찾을 수 없습니다.");

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenThrow(missingExecution);

        assertSame(
                missingExecution,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.preview(
                                EXECUTION_ID, USER_ID, 0L, 25)));

        verifyNoInteractions(
                csvPreviewService,
                resultAccessLogService);
    }

    @Test
    void missingContextCannotAllowUnloggedSuccessfulResponse() {
        when(resultAccessLogMapper.selectAccessContext(
                EXECUTION_ID, USER_ID))
                .thenReturn(null);

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.preview(
                        EXECUTION_ID, USER_ID, 0L, 25));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        verifyNoInteractions(
                csvPreviewService,
                resultAccessLogService);
    }

    private void prepareContext() {
        ResultAccessLogContext context = new ResultAccessLogContext();

        context.setRequestId(REQUEST_ID);
        context.setResultId(RESULT_ID);
        context.setUserId(USER_ID);

        when(resultAccessLogMapper.selectAccessContext(
                EXECUTION_ID, USER_ID))
                .thenReturn(context);
    }

    private void preparePreviewAccess() {
        prepareContext();

        when(executionResultService.getMyResultFile(
                EXECUTION_ID, USER_ID))
                .thenReturn(resultFile());
    }

    private ExecutionResultFile resultFile() {
        ExecutionResultFile file = new ExecutionResultFile();

        file.setExecutionId(EXECUTION_ID);
        file.setQueryId(50L);
        file.setResultId(RESULT_ID);
        file.setStatus("SUCCESS");
        file.setFilePath(FILE_PATH);

        return file;
    }

    private ExecutionResultPreviewResponse previewResponse() {
        return new ExecutionResultPreviewResponse(
                List.of("이름"),
                List.of(List.of("테스트")),
                1L,
                false);
    }

    private ResponseStatusException status(
            HttpStatus status,
            String reason) {

        return new ResponseStatusException(status, reason);
    }

    private void verifyLog(
            String actionType,
            String accessResult) {

        verify(resultAccessLogService)
                .record(
                        REQUEST_ID,
                        RESULT_ID,
                        USER_ID,
                        actionType,
                        accessResult);

        verifyNoMoreInteractions(resultAccessLogService);
    }
}