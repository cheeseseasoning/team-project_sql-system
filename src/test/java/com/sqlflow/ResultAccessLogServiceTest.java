package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ResultAccessLog;
import com.sqlflow.execution.mapper.ResultAccessLogMapper;
import com.sqlflow.execution.service.ResultAccessLogService;

@ExtendWith(MockitoExtension.class)
class ResultAccessLogServiceTest {

    private static final Long REQUEST_ID = 10L;
    private static final Long RESULT_ID = 20L;
    private static final Long USER_ID = 30L;

    @Mock
    private ResultAccessLogMapper mapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private ResultAccessLogService service;

    @BeforeEach
    void setUp() {
        service = new ResultAccessLogService(
                mapper,
                transactionManager);
    }

    @Test
    void savesCorrectValuesAndCommitsBeforeReturning() {
        prepareTransaction();

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenReturn(1);

        service.record(
                REQUEST_ID,
                RESULT_ID,
                USER_ID,
                "DOWNLOAD",
                "SUCCESS");

        ArgumentCaptor<ResultAccessLog> entryCaptor =
                ArgumentCaptor.forClass(ResultAccessLog.class);

        ArgumentCaptor<TransactionDefinition> definitionCaptor =
                ArgumentCaptor.forClass(TransactionDefinition.class);

        InOrder order = inOrder(transactionManager, mapper);

        order.verify(transactionManager)
                .getTransaction(definitionCaptor.capture());

        order.verify(mapper)
                .insertAccessLog(entryCaptor.capture());

        order.verify(transactionManager)
                .commit(transactionStatus);

        ResultAccessLog entry = entryCaptor.getValue();

        assertEquals(REQUEST_ID, entry.getRequestId());
        assertEquals(RESULT_ID, entry.getResultId());
        assertEquals(USER_ID, entry.getUserId());
        assertEquals("DOWNLOAD", entry.getActionType());
        assertEquals("SUCCESS", entry.getAccessResult());

        TransactionDefinition definition =
                definitionCaptor.getValue();

        assertEquals(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getPropagationBehavior());

        assertFalse(definition.isReadOnly());

        verify(transactionManager, never())
                .rollback(any(TransactionStatus.class));
    }

    @Test
    void allowsNullResultIdForDeniedAccess() {
        prepareTransaction();

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenReturn(1);

        service.record(
                REQUEST_ID,
                null,
                USER_ID,
                "PREVIEW",
                "DENIED");

        ArgumentCaptor<ResultAccessLog> captor =
                ArgumentCaptor.forClass(ResultAccessLog.class);

        verify(mapper).insertAccessLog(captor.capture());

        ResultAccessLog entry = captor.getValue();

        assertEquals(REQUEST_ID, entry.getRequestId());
        assertEquals(null, entry.getResultId());
        assertEquals("PREVIEW", entry.getActionType());
        assertEquals("DENIED", entry.getAccessResult());

        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void savesAdditionalPreviewError() {
        prepareTransaction();

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenReturn(1);

        service.record(
                REQUEST_ID,
                RESULT_ID,
                USER_ID,
                "PREVIEW_MORE",
                "ERROR");

        ArgumentCaptor<ResultAccessLog> captor =
                ArgumentCaptor.forClass(ResultAccessLog.class);

        verify(mapper).insertAccessLog(captor.capture());

        assertEquals(
                "PREVIEW_MORE",
                captor.getValue().getActionType());

        assertEquals(
                "ERROR",
                captor.getValue().getAccessResult());

        verify(transactionManager).commit(transactionStatus);
    }

    @Test
    void insertFailureRollsBackAndReturns503() {
        prepareTransaction();

        RuntimeException failure =
                new DataAccessResourceFailureException(
                        "테스트용 DB 연결 오류");

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenThrow(failure);

        ResponseStatusException error =
                assertUnavailable(() -> recordSuccess());

        assertSame(failure, error.getCause());

        verify(transactionManager).rollback(transactionStatus);

        verify(transactionManager, never())
                .commit(any(TransactionStatus.class));
    }

    @Test
    void unexpectedInsertCountRollsBackAndReturns503() {
        prepareTransaction();

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenReturn(0);

        assertUnavailable(() -> recordSuccess());

        verify(transactionManager).rollback(transactionStatus);

        verify(transactionManager, never())
                .commit(any(TransactionStatus.class));
    }

    @Test
    void commitFailureReturns503EvenAfterSuccessfulInsert() {
        prepareTransaction();

        when(mapper.insertAccessLog(any(ResultAccessLog.class)))
                .thenReturn(1);

        TransactionSystemException failure =
                new TransactionSystemException(
                        "테스트용 커밋 오류");

        doThrow(failure)
                .when(transactionManager)
                .commit(transactionStatus);

        ResponseStatusException error =
                assertUnavailable(() -> recordSuccess());

        assertSame(failure, error.getCause());

        verify(mapper).insertAccessLog(any(ResultAccessLog.class));
        verify(transactionManager).commit(transactionStatus);

        // 서비스가 저장을 재시도해 중복 로그를 만들지 않는지 확인한다.
        verify(transactionManager)
                .getTransaction(any(TransactionDefinition.class));
    }

    @Test
    void transactionStartFailureReturns503WithoutInsert() {
        CannotCreateTransactionException failure =
                new CannotCreateTransactionException(
                        "테스트용 트랜잭션 시작 오류");

        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenThrow(failure);

        ResponseStatusException error =
                assertUnavailable(() -> recordSuccess());

        assertSame(failure, error.getCause());

        verifyNoInteractions(mapper);

        verify(transactionManager, never())
                .commit(any(TransactionStatus.class));

        verify(transactionManager, never())
                .rollback(any(TransactionStatus.class));
    }

    @Test
    void invalidInputDoesNotStartTransaction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        null, RESULT_ID, USER_ID,
                        "DOWNLOAD", "SUCCESS"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        REQUEST_ID, RESULT_ID, null,
                        "DOWNLOAD", "SUCCESS"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        REQUEST_ID, 0L, USER_ID,
                        "DOWNLOAD", "SUCCESS"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "UNKNOWN", "SUCCESS"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.record(
                        REQUEST_ID, RESULT_ID, USER_ID,
                        "DOWNLOAD", "UNKNOWN"));

        verifyNoInteractions(mapper, transactionManager);
    }

    private void prepareTransaction() {
        when(transactionManager.getTransaction(
                any(TransactionDefinition.class)))
                .thenReturn(transactionStatus);
    }

    private void recordSuccess() {
        service.record(
                REQUEST_ID,
                RESULT_ID,
                USER_ID,
                "DOWNLOAD",
                "SUCCESS");
    }

    private ResponseStatusException assertUnavailable(
            Executable action) {

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                action);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        assertEquals(
                "접근 기록을 저장하지 못해 결과를 제공할 수 없습니다. "
                + "잠시 후 다시 시도해 주세요.",
                error.getReason());

        return error;
    }
}