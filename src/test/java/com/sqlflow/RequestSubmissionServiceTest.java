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
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;
import com.sqlflow.request.dto.RequestSqlValidationResult;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.ApprovalLineCreator;
import com.sqlflow.request.service.RequestService;
import com.sqlflow.request.service.ViewCreateSqlValidationService;

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
                requestSqlValidationService,
                new ViewCreateSqlValidationService());
    }

    @Test
    void submissionCreatesApprovalLineBeforeChangingRequestStatus() {
        RequestItemResponse item = new RequestItemResponse();
        item.setQueryId(101L);
        item.setSqlText("SELECT * FROM sqlflow_demo.demo_product");

        prepareDraft(List.of(item));

        when(approvalLineCreatorProvider.getIfAvailable())
                .thenReturn(approvalLineCreator);

        when(requestSqlValidationService.validateForSubmission(
                1L, List.of(item)))
                .thenReturn(List.of(
                        new RequestSqlValidationResult(
                                101L,
                                "VALID",
                                "검증 통과",
                                "GENERAL",
                                List.of(1L)
                        )
                ));

        when(requestMapper.updateSubmittedItemValidation(
                2L, 101L, "GENERAL")).thenReturn(1);

        when(requestMapper.insertQueryResourceUsage(
                101L, 1L)).thenReturn(1);

        when(requestMapper.updateRequestClass(
                2L, 1L, "GENERAL")).thenReturn(1);

        when(requestMapper.submitDraft(2L, 1L)).thenReturn(1);

        requestService.submitRequest(2L, submitInput(), 1L);

        InOrder order = inOrder(
                requestMapper,
                requestSqlValidationService,
                approvalLineCreator
        );

        order.verify(requestMapper)
                .selectMyRequestStatusForUpdate(2L, 1L);

        order.verify(requestSqlValidationService)
                .validateForSubmission(1L, List.of(item));

        order.verify(requestMapper)
                .updateSubmittedItemValidation(2L, 101L, "GENERAL");

        order.verify(requestMapper)
                .deleteQueryResourceUsage(101L);

        order.verify(requestMapper)
                .insertQueryResourceUsage(101L, 1L);

        order.verify(requestMapper)
                .updateRequestClass(2L, 1L, "GENERAL");

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
		        .validateForSubmission(1L, List.of(item));

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
		        .validateForSubmission(1L, List.of(item));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.submitRequest(2L, submitInput(), 1L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());

        verifyNoInteractions(approvalLineCreator);
        verify(requestMapper, never()).submitDraft(any(), any());
    }

    @Test
    void draftValidationStoresEachSqlStatusAndReturnsResults() {
        RequestItemResponse first = new RequestItemResponse();
        first.setQueryId(101L);
        first.setSqlText("SELECT 1 FROM BIZ_DATA.SALES");
        RequestItemResponse second = new RequestItemResponse();
        second.setQueryId(102L);
        second.setSqlText("SELECT * FROM BIZ_DATA.UNKNOWN_TABLE");
        when(requestMapper.selectMyRequestStatusForUpdate(2L, 1L))
                .thenReturn("DRAFT");
        when(requestMapper.selectMyRequestItems(2L, 1L))
                .thenReturn(List.of(first, second));

        List<RequestSqlValidationResult> results = List.of(
                new RequestSqlValidationResult(101L, "VALID", "검증을 통과했습니다.", "GENERAL", List.of(4L)),
                new RequestSqlValidationResult(102L, "INVALID", "테이블을 찾을 수 없습니다.", null, List.of()));
        when(requestSqlValidationService.validateItems(1L, List.of(first, second)))
                .thenReturn(results);
        when(requestMapper.updateDraftItemValidationStatus(2L, 101L, 1L, "VALID"))
                .thenReturn(1);
        when(requestMapper.updateDraftItemValidationStatus(2L, 102L, 1L, "INVALID"))
                .thenReturn(1);

        assertEquals(results, requestService.validateDraftSql(2L, 1L));
        verify(requestMapper).updateDraftItemValidationStatus(2L, 101L, 1L, "VALID");
        verify(requestMapper).updateDraftItemValidationStatus(2L, 102L, 1L, "INVALID");
    }

    @Test
    void nonDraftCannotRunManualSqlValidation() {
        when(requestMapper.selectMyRequestStatusForUpdate(2L, 1L))
                .thenReturn("PENDING_APPROVAL");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.validateDraftSql(2L, 1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verifyNoInteractions(requestSqlValidationService);
    }
    
    @Test
    void viewDraftValidationReturnsResultsBySqlId() {
        RequestDetailResponse draft = new RequestDetailResponse();
        draft.setRequestType("CREATE_VIEW");
        draft.setRequestStatus("DRAFT");

        RequestItemResponse validItem = new RequestItemResponse();
        validItem.setQueryId(101L);
        validItem.setSqlText(
                "CREATE VIEW v_test AS SELECT 1 AS id");

        RequestItemResponse warningItem = new RequestItemResponse();
        warningItem.setQueryId(102L);
        warningItem.setSqlText(
                "CREATE VIEW v_bad AS SELECT * FROM");

        when(requestMapper.selectMyRequestDetail(2L, 1L))
                .thenReturn(draft);

        when(requestMapper.selectMyViewSqlItems(2L, 1L))
                .thenReturn(List.of(validItem, warningItem));

        var results = requestService.validateViewDraftSql(2L, 1L);

        assertEquals(2, results.size());
        assertEquals(Status.VALID, results.get(101L).getStatus());
        assertEquals(Status.WARNING, results.get(102L).getStatus());

        // 검사만 수행하며 SELECT 검증·결재 생성은 호출하지 않는다.
        verifyNoInteractions(
                requestSqlValidationService,
                approvalLineCreator);
    }

    @Test
    void viewDraftValidationRejectsRequestNotOwnedByActor() {
        // 작성자 조건으로 조회되지 않으면 SQL 원문도 조회하지 않는다.
        when(requestMapper.selectMyRequestDetail(2L, 99L))
                .thenReturn(null);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.validateViewDraftSql(2L, 99L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(requestMapper, never())
                .selectMyViewSqlItems(any(), any());
    }

    @Test
    void viewDraftValidationRejectsSubmittedRequest() {
        RequestDetailResponse draft = new RequestDetailResponse();
        draft.setRequestType("CREATE_VIEW");
        draft.setRequestStatus("PENDING_APPROVAL");

        when(requestMapper.selectMyRequestDetail(2L, 1L))
                .thenReturn(draft);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> requestService.validateViewDraftSql(2L, 1L));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());

        verify(requestMapper, never())
                .selectMyViewSqlItems(any(), any());
    }
}
