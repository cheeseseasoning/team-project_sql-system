package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestSubmitRequest;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.ApprovalLineCreator;
import com.sqlflow.request.service.RequestService;

@ExtendWith(MockitoExtension.class)
class RequestSubmissionServiceTest {

    @Mock
    private RequestMapper requestMapper;

    @Mock
    private ObjectProvider<ApprovalLineCreator> approvalLineCreatorProvider;

    @Mock
    private ApprovalLineCreator approvalLineCreator;
    
    @Mock
    private RequestSqlValidationService requestSqlValidationService;

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestMapper,
                approvalLineCreatorProvider,
                requestSqlValidationService);
    }

    @Test
    void submissionCreatesApprovalLineBeforeChangingRequestStatus() {
        RequestItemResponse item = new RequestItemResponse();
        item.setSqlText("SELECT 1 FROM DUAL");

        prepareDraft(List.of(item));

        when(approvalLineCreatorProvider.getIfAvailable())
                .thenReturn(approvalLineCreator);

        when(requestMapper.submitDraft(2L, 1L)).thenReturn(1);

        requestService.submitRequest(2L, submitInput(), 1L);

     // SQL·권한 검사까지 통과해야 결재선과 제출 상태를 생성한다.
        InOrder order = inOrder(
                requestMapper,
                requestSqlValidationService,
                approvalLineCreator);

        order.verify(requestMapper).selectMyRequestStatusForUpdate(2L, 1L);

        order.verify(requestSqlValidationService).validate(1L, List.of("SELECT 1 FROM DUAL"));

        order.verify(approvalLineCreator)
                .createApprovalLine(2L, 1L, List.of(), 20L);

        order.verify(requestMapper).submitDraft(2L, 1L);
    }

    @Test
    void submissionWithoutSqlIsRejected() {
        prepareDraft(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(2L, submitInput(), 1L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());

        verify(approvalLineCreator, never())
                .createApprovalLine(any(), any(), any(), any());

        verify(requestMapper, never()).submitDraft(any(), any());
    }

    @Test
    void submissionWithoutApprovalIntegrationIsRejected() {
        RequestItemResponse item = new RequestItemResponse();
        item.setSqlText("SELECT 1 FROM DUAL");

        prepareDraft(List.of(item));

        when(approvalLineCreatorProvider.getIfAvailable())
                .thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(2L, submitInput(), 1L)
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                error.getStatusCode()
        );

        verify(requestMapper, never()).submitDraft(any(), any());
    }

    private void prepareDraft(List<RequestItemResponse> items) {
        RequestDetailResponse request = new RequestDetailResponse();
        request.setTitle("제출 테스트");
        request.setPurpose("제출 조건과 처리 순서 확인");

        when(requestMapper.selectMyRequestStatusForUpdate(2L, 1L))
                .thenReturn("DRAFT");

        when(requestMapper.selectMyRequestDetail(2L, 1L))
                .thenReturn(request);

        when(requestMapper.selectMyRequestItems(2L, 1L))
                .thenReturn(items);
    }

    private RequestSubmitRequest submitInput() {
        RequestSubmitRequest input = new RequestSubmitRequest();
        input.setDbApproverId(20L);
        return input;
    }
    
    @Test
    void accessDeniedStopsApprovalAndSubmission() {
        RequestItemResponse item = new RequestItemResponse();
        item.setSqlText("SELECT * FROM BIZ_DATA.CUSTOMERS");

        prepareDraft(List.of(item));

        when(approvalLineCreatorProvider.getIfAvailable())
                .thenReturn(approvalLineCreator);

        doThrow(new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "조회 권한이 없는 테이블이 포함되어 있습니다."))
                .when(requestSqlValidationService)
                .validate(1L, List.of(item.getSqlText()));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(2L, submitInput(), 1L));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());

        // 권한이 없으면 결재선 생성과 제출 상태 변경을 모두 하지 않는다.
        verifyNoInteractions(approvalLineCreator);
        verify(requestMapper, never()).submitDraft(any(), any());
    }

    @Test
    void invalidSqlStopsApprovalAndSubmission() {
        RequestItemResponse item = new RequestItemResponse();
        item.setSqlText("DELETE FROM BIZ_DATA.SALES");

        prepareDraft(List.of(item));

        when(approvalLineCreatorProvider.getIfAvailable())
                .thenReturn(approvalLineCreator);

        doThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "SELECT만 제출할 수 있습니다."))
                .when(requestSqlValidationService)
                .validate(1L, List.of(item.getSqlText()));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(2L, submitInput(), 1L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());

        verifyNoInteractions(approvalLineCreator);
        verify(requestMapper, never()).submitDraft(any(), any());
    }
}
