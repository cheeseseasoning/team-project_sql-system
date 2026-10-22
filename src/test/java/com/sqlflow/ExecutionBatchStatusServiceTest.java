package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionBatchStatusResponseDTO;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.service.ExecutionBatchStatusService;

/**
 * 상태 조회 결과 전달과 잘못된 요청 처리를 확인한다.
 * 요청자·DB_APPROVER 판정 SQL은 Oracle 연동 테스트에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionBatchStatusServiceTest {

    private static final Long BATCH_ID = 100L;
    private static final Long ACTOR_ID = 10L;

    @Mock
    private ExecutionBatchMapper mapper;

    @Mock
    private SqlFlowUserDetails user;

    @InjectMocks
    private ExecutionBatchStatusService service;

    @Test
    void returnsVisibleBatchStatus() {
        when(user.getUserId()).thenReturn(ACTOR_ID);

        var expected = new ExecutionBatchStatusResponseDTO();
        expected.setBatchId(BATCH_ID);
        expected.setBatchStatus("RUNNING");
        expected.setTotalCount(3);
        expected.setSuccessCount(1);
        expected.setFailureCount(0);
        expected.setRunningCount(1);
        expected.setPendingCount(1);

        when(mapper.selectVisibleBatchStatus(BATCH_ID, ACTOR_ID))
                .thenReturn(expected);

        assertSame(expected, service.getStatus(BATCH_ID, user));

        verify(mapper).selectVisibleBatchStatus(BATCH_ID, ACTOR_ID);
    }

    @Test
    void invisibleOrMissingBatchReturns404() {
        when(user.getUserId()).thenReturn(ACTOR_ID);
        when(mapper.selectVisibleBatchStatus(BATCH_ID, ACTOR_ID))
                .thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getStatus(BATCH_ID, user));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void unauthenticatedUserCannotReadStatus() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getStatus(BATCH_ID, null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void invalidBatchIdIsRejectedBeforeQuerying() {
        when(user.getUserId()).thenReturn(ACTOR_ID);

        for (Long batchId : new Long[] { null, 0L, -1L }) {
            ResponseStatusException exception = assertThrows(
                    ResponseStatusException.class,
                    () -> service.getStatus(batchId, user));

            assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        }

        verifyNoInteractions(mapper);
    }
}