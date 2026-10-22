package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.domain.QueryResult;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.mapper.QueryResultMapper;
import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;
import com.sqlflow.execution.service.QueryExecutionRecordService;

/**
 * 실행 상태와 결과 정보를 올바르게 전달하는지 확인한다.
 * 실제 DB 저장과 트랜잭션 롤백은 별도 연동 테스트에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class QueryExecutionRecordServiceTest {

    private static final Long EXECUTION_ID = 100L;

    @Mock
    private QueryExecutionMapper executionMapper;

    @Mock
    private QueryResultMapper resultMapper;

    @InjectMocks
    private QueryExecutionRecordService service;

    @Test
    void startsPendingExecution() {
        when(executionMapper.markRunning(EXECUTION_ID)).thenReturn(1);

        assertDoesNotThrow(() -> service.start(EXECUTION_ID));

        verify(executionMapper).markRunning(EXECUTION_ID);
        verifyNoInteractions(resultMapper);
    }

    @Test
    void rejectsExecutionThatCannotBeStarted() {
        when(executionMapper.markRunning(EXECUTION_ID)).thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.start(EXECUTION_ID));

        verifyNoInteractions(resultMapper);
    }

    @Test
    void savesResultMetadataAndSuccessStatus() {
        when(executionMapper.finishExecution(any(QueryExecution.class)))
                .thenReturn(1);
        when(resultMapper.insertResult(any(QueryResult.class))).thenReturn(1);

        ExecutionResult result = new ExecutionResult(
                "query-test.csv", 25L, 2048L, 150L);

        service.complete(EXECUTION_ID, result);

        var executionCaptor = ArgumentCaptor.forClass(QueryExecution.class);
        var resultCaptor = ArgumentCaptor.forClass(QueryResult.class);

        verify(executionMapper).finishExecution(executionCaptor.capture());
        verify(resultMapper).insertResult(resultCaptor.capture());

        QueryExecution execution = executionCaptor.getValue();
        assertEquals(EXECUTION_ID, execution.getExecutionId());
        assertEquals("SUCCESS", execution.getStatus());
        assertEquals(150L, execution.getDurationMs().longValue());
        assertNull(execution.getErrorCode());
        assertNull(execution.getErrorMessage());

        QueryResult saved = resultCaptor.getValue();
        assertEquals(EXECUTION_ID, saved.getExecutionId());
        assertEquals("query-test.csv", saved.getFilePath());
        assertEquals(25L, saved.getRowCount().longValue());
        assertEquals(2048L, saved.getFileSize().longValue());
    }

    @Test
    void doesNotInsertResultWhenExecutionCannotBeCompleted() {
        when(executionMapper.finishExecution(any(QueryExecution.class)))
                .thenReturn(0);

        ExecutionResult result = new ExecutionResult(
                "query-test.csv", 25L, 2048L, 150L);

        assertThrows(
                IllegalStateException.class,
                () -> service.complete(EXECUTION_ID, result));

        verifyNoInteractions(resultMapper);
    }

    @Test
    void reportsResultMetadataSaveFailure() {
        when(executionMapper.finishExecution(any(QueryExecution.class)))
                .thenReturn(1);
        when(resultMapper.insertResult(any(QueryResult.class))).thenReturn(0);

        ExecutionResult result = new ExecutionResult(
                "query-test.csv", 25L, 2048L, 150L);

        assertThrows(
                IllegalStateException.class,
                () -> service.complete(EXECUTION_ID, result));
    }

    @Test
    void savesFailureDetailsWithoutResultMetadata() {
        when(executionMapper.finishExecution(any(QueryExecution.class)))
                .thenReturn(1);

        service.fail(
                EXECUTION_ID, 300L, "MYSQL_1054", "존재하지 않는 컬럼입니다.");

        var captor = ArgumentCaptor.forClass(QueryExecution.class);
        verify(executionMapper).finishExecution(captor.capture());

        QueryExecution execution = captor.getValue();
        assertEquals(EXECUTION_ID, execution.getExecutionId());
        assertEquals("FAILED", execution.getStatus());
        assertEquals(300L, execution.getDurationMs().longValue());
        assertEquals("MYSQL_1054", execution.getErrorCode());
        assertEquals("존재하지 않는 컬럼입니다.", execution.getErrorMessage());

        verifyNoInteractions(resultMapper);
    }

    @Test
    void reportsFailureWhenExecutionCannotBeMarkedFailed() {
        when(executionMapper.finishExecution(any(QueryExecution.class)))
                .thenReturn(0);

        assertThrows(
                IllegalStateException.class,
                () -> service.fail(EXECUTION_ID, 300L, "IO_ERROR", "파일 저장 실패"));

        verifyNoInteractions(resultMapper);
    }
}