package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private RequestService requestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestMapper,
                approvalLineCreatorProvider
        );
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

        InOrder order = inOrder(requestMapper, approvalLineCreator);

        order.verify(requestMapper)
                .selectMyRequestStatusForUpdate(2L, 1L);

        order.verify(approvalLineCreator)
                .createApprovalLine(2L, 1L, 20L);

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
                .createApprovalLine(any(), any(), any());

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
}
