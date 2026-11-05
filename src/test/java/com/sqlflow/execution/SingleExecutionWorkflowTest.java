package com.sqlflow.execution;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;
import com.sqlflow.execution.service.QueryExecutionService;
import com.sqlflow.execution.service.RequestExecutionStatusService;
import com.sqlflow.execution.service.SingleExecutionDispatchService;
import com.sqlflow.execution.service.SingleExecutionRecordService;
import com.sqlflow.execution.service.SingleExecutionRecordService.SingleStartResult;

@ExtendWith(MockitoExtension.class)
class SingleExecutionWorkflowTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long QUERY_ID = 200L;
    private static final Long ACTOR_ID = 300L;
    private static final Long EXECUTION_ID = 400L;

    @Mock
    private ExecutionBatchMapper requestMapper;

    @Mock
    private QueryExecutionMapper executionMapper;

    @Mock
    private SingleExecutionRecordService recordService;

    @Mock
    private QueryExecutionService queryExecutionService;

    @Mock
    private Executor executor;

    private RequestExecutionStatusService statusService;
    private SingleExecutionDispatchService dispatchService;

    @BeforeEach
    void setUp() {
        statusService = new RequestExecutionStatusService(
                requestMapper, executionMapper);

        dispatchService = new SingleExecutionDispatchService(
                recordService,
                queryExecutionService,
                statusService,
                executor);
    }

    @ParameterizedTest
    @CsvSource({
        "SUCCESS, SUCCESS, COMPLETED",
        "SUCCESS, FAILED, PARTIAL_FAILED",
        "FAILED, FAILED, FAILED"
    })
    void 모든_SQL의_최신_상태로_기안서_결과를_집계한다(
            String firstStatus,
            String secondStatus,
            String expectedStatus) {
        prepareRefresh(List.of(
                execution(firstStatus),
                execution(secondStatus)));

        when(requestMapper.updateRequestStatus(
                REQUEST_ID, "RUNNING", expectedStatus)).thenReturn(1);

        assertEquals(
                expectedStatus,
                statusService.refresh(REQUEST_ID));

        verify(requestMapper).updateRequestStatus(
                REQUEST_ID, "RUNNING", expectedStatus);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"PENDING", "RUNNING"})
    void 미실행이나_진행중_SQL이_있으면_완료하지_않는다(
            String unfinishedStatus) {
        prepareRefresh(List.of(
                execution("SUCCESS"),
                execution(unfinishedStatus)));

        assertEquals("RUNNING", statusService.refresh(REQUEST_ID));

        verify(requestMapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    @Test
    void 접수된_작업은_백그라운드에서_공통실행과_집계를_수행한다() {
        var started =
                new SingleStartResult(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(recordService.reserve(
                REQUEST_ID, QUERY_ID, ACTOR_ID)).thenReturn(started);

        var accepted =
                dispatchService.submit(
                        REQUEST_ID, QUERY_ID, loginUser());

        assertEquals(started, accepted);

        // 아직 백그라운드 작업을 실행하지 않았다.
        verifyNoInteractions(queryExecutionService);

        ArgumentCaptor<Runnable> task =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(task.capture());

        prepareRefresh(List.of(execution("SUCCESS")));

        when(requestMapper.updateRequestStatus(
                REQUEST_ID, "RUNNING", "COMPLETED")).thenReturn(1);

        task.getValue().run();

        verify(queryExecutionService)
                .executeStarted(EXECUTION_ID);

        verify(requestMapper).updateRequestStatus(
                REQUEST_ID, "RUNNING", "COMPLETED");
    }

    @Test
    void 실행기가_접수를_거절하면_복구하고_503을_반환한다() {
        var started =
                new SingleStartResult(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(recordService.reserve(
                REQUEST_ID, QUERY_ID, ACTOR_ID)).thenReturn(started);

        doThrow(new RejectedExecutionException("실행기 사용 중"))
                .when(executor).execute(any(Runnable.class));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> dispatchService.submit(
                        REQUEST_ID, QUERY_ID, loginUser()));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode());

        verify(recordService).cancelRejectedStart(started);

        verifyNoInteractions(
                queryExecutionService, requestMapper, executionMapper);
    }

    @Test
    void 예약_복구도_실패하면_500을_반환한다() {
        var started =
                new SingleStartResult(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(recordService.reserve(
                REQUEST_ID, QUERY_ID, ACTOR_ID)).thenReturn(started);

        doThrow(new RejectedExecutionException("실행기 사용 중"))
                .when(executor).execute(any(Runnable.class));

        doThrow(new IllegalStateException("예약 복구 실패"))
                .when(recordService).cancelRejectedStart(started);

        var error = assertThrows(
                ResponseStatusException.class,
                () -> dispatchService.submit(
                        REQUEST_ID, QUERY_ID, loginUser()));

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                error.getStatusCode());

        verifyNoInteractions(queryExecutionService);
    }

    @Test
    void 실행중_장애가_나도_저장된_상태가_RUNNING이면_완료하지_않는다() {
        var started =
                new SingleStartResult(
                        REQUEST_ID, QUERY_ID, EXECUTION_ID);

        when(recordService.reserve(
                REQUEST_ID, QUERY_ID, ACTOR_ID)).thenReturn(started);

        dispatchService.submit(
                REQUEST_ID, QUERY_ID, loginUser());

        ArgumentCaptor<Runnable> task =
                ArgumentCaptor.forClass(Runnable.class);

        verify(executor).execute(task.capture());

        doThrow(new IllegalStateException("결과 저장 상태 확인 필요"))
                .when(queryExecutionService)
                .executeStarted(EXECUTION_ID);

        prepareRefresh(List.of(execution("RUNNING")));

        task.getValue().run();

        verify(requestMapper, never()).updateRequestStatus(
                anyLong(), anyString(), anyString());
    }

    private void prepareRefresh(List<QueryExecution> executions) {
        ExecutionRequestContext request =
                new ExecutionRequestContext();

        request.setRequestId(REQUEST_ID);
        request.setApprovalStatus("APPROVED");
        request.setRequestStatus("RUNNING");

        when(requestMapper.selectRequestContextForUpdate(REQUEST_ID))
                .thenReturn(request);

        when(executionMapper.selectLatestExecutionsByRequestId(REQUEST_ID))
                .thenReturn(executions);
    }

    private QueryExecution execution(String status) {
        QueryExecution execution = new QueryExecution();
        execution.setStatus(status);
        return execution;
    }

    private SqlFlowUserDetails loginUser() {
        UserAccount account = new UserAccount();
        account.setUserId(ACTOR_ID);
        account.setLoginId("execution-test");
        account.setUserName("실행 테스트");
        account.setEnabled("Y");

        return new SqlFlowUserDetails(
                account, List.of("DB_APPROVER"));
    }
}