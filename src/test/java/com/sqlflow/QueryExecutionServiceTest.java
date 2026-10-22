package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.domain.QueryExecutionTarget;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.MySqlQueryExecutionService;
import com.sqlflow.execution.service.MySqlQueryExecutionService.ExecutionResult;
import com.sqlflow.execution.service.QueryExecutionRecordService;
import com.sqlflow.execution.service.QueryExecutionService;

/**
 * 단일 SQL의 재검증·실행·결과 기록 연결을 확인한다.
 * 실제 JDBC 실행과 Oracle 트랜잭션은 별도 연동 테스트에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class QueryExecutionServiceTest {

    private static final Long EXECUTION_ID = 100L;
    private static final Long DRAFTER_ID = 10L;
    private static final String SQL =
            "SELECT department_id FROM demo_department";

    @Mock
    private QueryExecutionMapper executionMapper;

    @Mock
    private RequestSqlValidationService validationService;

    @Mock
    private MySqlQueryExecutionService mySqlService;

    @Mock
    private QueryExecutionRecordService recordService;

    @InjectMocks
    private QueryExecutionService service;

    private QueryExecutionTarget target;
    private ExecutionResult fileResult;

    @BeforeEach
    void setUp() {
        target = new QueryExecutionTarget();
        target.setExecutionId(EXECUTION_ID);
        target.setBatchId(20L);
        target.setRequestId(30L);
        target.setQueryId(40L);
        target.setDrafterId(DRAFTER_ID);
        target.setExecutionStatus("PENDING");
        target.setBatchStatus("RUNNING");
        target.setRequestStatus("RUNNING");
        target.setApprovalStatus("APPROVED");
        target.setSqlText(SQL);

        fileResult = new ExecutionResult(
                "query-test.csv", 4L, 512L, 120L);
    }

    @Test
    void revalidatesOriginalRequesterBeforeExecuting() throws Exception {
        prepareTarget();
        when(mySqlService.execute(SQL)).thenReturn(fileResult);

        var outcome = service.execute(EXECUTION_ID);

        assertEquals(EXECUTION_ID, outcome.executionId());
        assertTrue(outcome.success());

        // 시작 → 원래 요청자 재검증 → MySQL 실행 → 결과 저장 순서를 확인한다.
        var order = inOrder(recordService, validationService, mySqlService);
        order.verify(recordService).start(EXECUTION_ID);
        order.verify(validationService).validate(DRAFTER_ID, List.of(SQL));
        order.verify(mySqlService).execute(SQL);
        order.verify(recordService).complete(EXECUTION_ID, fileResult);

        verify(recordService, never())
                .fail(anyLong(), anyLong(), anyString(), anyString());
        verify(mySqlService, never()).deleteResultFile(any());
    }

    @Test
    void accessRevalidationFailurePreventsSqlExecution() {
        prepareTarget();

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN, "민감 데이터 조회 권한이 없습니다."))
                .when(validationService)
                .validate(DRAFTER_ID, List.of(SQL));

        var outcome = service.execute(EXECUTION_ID);

        assertFalse(outcome.success());

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("REVALIDATION_FAILED"),
                eq("민감 데이터 조회 권한이 없습니다."));

        verifyNoInteractions(mySqlService);
        verify(recordService, never()).complete(anyLong(), any());
    }

    @Test
    void sqlFailureIsRecordedWithoutCompleting() throws Exception {
        prepareTarget();

        when(mySqlService.execute(SQL)).thenThrow(
                new SQLException("없는 컬럼입니다.", "42S22", 1054));

        var outcome = service.execute(EXECUTION_ID);

        assertFalse(outcome.success());

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("MYSQL_1054"),
                eq("없는 컬럼입니다."));

        verify(recordService, never()).complete(anyLong(), any());

        // 실행 서비스가 생성 중 파일 정리를 담당하므로 여기서는 중복 삭제하지 않는다.
        verify(mySqlService, never()).deleteResultFile(any());
    }

    @Test
    void fileFailureIsRecordedWithoutCompleting() throws Exception {
        prepareTarget();

        when(mySqlService.execute(SQL)).thenThrow(
                new IOException("파일 크기 제한 초과"));

        var outcome = service.execute(EXECUTION_ID);

        assertFalse(outcome.success());

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("RESULT_FILE_ERROR"),
                eq("파일 크기 제한 초과"));

        verify(recordService, never()).complete(anyLong(), any());
    }

    @Test
    void resultConstraintFailureRecordsFailureAndDeletesFile() throws Exception {
        prepareTarget();
        when(mySqlService.execute(SQL)).thenReturn(fileResult);

        doThrow(new DataIntegrityViolationException("결과 저장 제약 위반"))
                .when(recordService)
                .complete(EXECUTION_ID, fileResult);

        var outcome = service.execute(EXECUTION_ID);

        assertFalse(outcome.success());

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("RESULT_SAVE_FAILED"),
                eq("결과 저장 제약 위반"));

        verify(mySqlService).deleteResultFile(fileResult);
    }

    @Test
    void uncertainDatabaseFailurePreservesFileAndStopsProcessing() throws Exception {
        prepareTarget();
        when(mySqlService.execute(SQL)).thenReturn(fileResult);

        var failure = new DataAccessResourceFailureException("Oracle 연결 단절");

        doThrow(failure)
                .when(recordService)
                .complete(EXECUTION_ID, fileResult);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> service.execute(EXECUTION_ID));

        assertSame(failure, actual.getCause());
        assertTrue(actual.getMessage().contains("query-test.csv"));

        // 커밋 성공 여부가 불명확하면 파일 삭제나 실패 상태 덮어쓰기를 하지 않는다.
        verify(mySqlService, never()).deleteResultFile(any());
        verify(recordService, never())
                .fail(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void startFailurePreventsValidationAndExecution() {
        prepareTarget();

        var failure = new IllegalStateException("이미 시작된 실행입니다.");

        doThrow(failure)
                .when(recordService)
                .start(EXECUTION_ID);

        assertSame(failure, assertThrows(
                IllegalStateException.class,
                () -> service.execute(EXECUTION_ID)));

        verifyNoInteractions(validationService, mySqlService);
        verify(recordService, never())
                .fail(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void requestThatIsNotRunningCannotExecute() {
        prepareTarget();
        target.setRequestStatus("READY");

        ResponseStatusException actual = assertThrows(
                ResponseStatusException.class,
                () -> service.execute(EXECUTION_ID));

        assertEquals(HttpStatus.CONFLICT, actual.getStatusCode());

        verifyNoInteractions(recordService, validationService, mySqlService);
    }

    @Test
    void unexpectedExecutionFailureIsRecordedAndRethrown() throws Exception {
        prepareTarget();

        var failure = new IllegalStateException("예상하지 못한 실행 오류");
        when(mySqlService.execute(SQL)).thenThrow(failure);

        assertSame(failure, assertThrows(
                IllegalStateException.class,
                () -> service.execute(EXECUTION_ID)));

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("EXECUTION_ERROR"),
                eq("예상하지 못한 실행 오류"));

        verify(recordService, never()).complete(anyLong(), any());
    }

    @Test
    void cleanupFailureIsReportedAfterRecordingFailure() throws Exception {
        prepareTarget();
        when(mySqlService.execute(SQL)).thenReturn(fileResult);

        var saveFailure =
                new DataIntegrityViolationException("결과 저장 제약 위반");
        var cleanupFailure = new IOException("공유 폴더 파일 삭제 실패");

        doThrow(saveFailure)
                .when(recordService)
                .complete(EXECUTION_ID, fileResult);

        doThrow(cleanupFailure)
                .when(mySqlService)
                .deleteResultFile(fileResult);

        IllegalStateException actual = assertThrows(
                IllegalStateException.class,
                () -> service.execute(EXECUTION_ID));

        assertSame(saveFailure, actual.getCause());
        assertArrayEquals(
                new Throwable[] { cleanupFailure },
                saveFailure.getSuppressed());

        verify(recordService).fail(
                eq(EXECUTION_ID),
                anyLong(),
                eq("RESULT_SAVE_FAILED"),
                eq("결과 저장 제약 위반"));
    }

    private void prepareTarget() {
        when(executionMapper.selectExecutionTarget(EXECUTION_ID))
                .thenReturn(target);
    }
}