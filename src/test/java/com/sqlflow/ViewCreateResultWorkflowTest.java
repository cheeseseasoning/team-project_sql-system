package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.ViewCreateResultResponse;
import com.sqlflow.request.mapper.DepartmentRequestMapper;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.ApprovalLineCreator;
import com.sqlflow.request.service.DepartmentRequestService;
import com.sqlflow.request.service.RequestService;
import com.sqlflow.request.service.ViewCreateSqlValidationService;

@ExtendWith(MockitoExtension.class)
class ViewCreateResultWorkflowTest {

    @Mock
    private RequestMapper requestMapper;

    @Mock
    private DepartmentRequestMapper departmentRequestMapper;

    @Mock
    private ObjectProvider<ApprovalLineCreator> approvalLineCreatorProvider;

    @Mock
    private RequestSqlValidationService requestSqlValidationService;

    private RequestService requestService;
    private DepartmentRequestService departmentRequestService;

    @BeforeEach
    void setUp() {
        requestService = new RequestService(
                requestMapper,
                approvalLineCreatorProvider,
                requestSqlValidationService,
                new ViewCreateSqlValidationService());

        departmentRequestService =
                new DepartmentRequestService(departmentRequestMapper);
    }

    @Test
    void downloadAddsOneTerminatorOnlyWhenSqlDoesNotAlreadyHaveOne() {
        when(requestMapper.canDownloadApprovedViewSql(42L, 7L)).thenReturn(1);
        RequestItemResponse first = sqlItem(1, "CREATE VIEW v_one AS SELECT 1 FROM DUAL");
        RequestItemResponse second = sqlItem(2, "CREATE VIEW v_two AS SELECT 2 FROM DUAL;  \n");
        when(requestMapper.selectApprovedViewSqlItems(42L))
                .thenReturn(List.of(first, second));

        String file = new String(
                requestService.downloadApprovedViewSql(42L, 7L),
                StandardCharsets.UTF_8);

        assertTrue(file.contains("CREATE VIEW v_one AS SELECT 1 FROM DUAL\n;\n\n-- SQL 2"));
        assertTrue(file.contains("CREATE VIEW v_two AS SELECT 2 FROM DUAL;\n\n"));
        assertEquals(2, file.chars().filter(character -> character == ';').count());
    }

    @Test
    void firstExternalResultDoesNotRequireAChangeReasonOrCreateHistory() {
        when(departmentRequestMapper.lockEligibleViewCreateRequest(42L, 7L))
                .thenReturn(42L);
        when(departmentRequestMapper.selectVisibleViewCreateResult(42L, 7L))
                .thenReturn(null);
        when(departmentRequestMapper.insertViewCreateResult(
                42L, 7L, "COMPLETED", "뷰가 생성되었습니다."))
                .thenReturn(1);

        departmentRequestService.registerViewCreateResult(
                42L, 7L, "COMPLETED", "뷰가 생성되었습니다.", null);

        verify(departmentRequestMapper).insertViewCreateResult(
                42L, 7L, "COMPLETED", "뷰가 생성되었습니다.");
        verify(departmentRequestMapper, never())
                .insertViewCreateResultHistory(42L, 7L, null);
    }

    @Test
    void changingExternalResultArchivesPreviousValueBeforeUpdating() {
        when(departmentRequestMapper.lockEligibleViewCreateRequest(42L, 7L))
                .thenReturn(42L);
        when(departmentRequestMapper.selectVisibleViewCreateResult(42L, 7L))
                .thenReturn(new ViewCreateResultResponse());
        when(departmentRequestMapper.insertViewCreateResultHistory(
                42L, 7L, "정정된 실행 로그 반영"))
                .thenReturn(1);
        when(departmentRequestMapper.updateViewCreateResult(
                42L, 7L, "FAILED", "실행 결과를 수정했습니다."))
                .thenReturn(1);

        departmentRequestService.registerViewCreateResult(
                42L, 7L, "FAILED", "실행 결과를 수정했습니다.", " 정정된 실행 로그 반영 ");

        InOrder order = inOrder(departmentRequestMapper);
        order.verify(departmentRequestMapper).lockEligibleViewCreateRequest(42L, 7L);
        order.verify(departmentRequestMapper).selectVisibleViewCreateResult(42L, 7L);
        order.verify(departmentRequestMapper).insertViewCreateResultHistory(
                42L, 7L, "정정된 실행 로그 반영");
        order.verify(departmentRequestMapper).updateViewCreateResult(
                42L, 7L, "FAILED", "실행 결과를 수정했습니다.");
    }

    @Test
    void changingExternalResultWithoutReasonIsRejectedBeforeWritingHistory() {
        when(departmentRequestMapper.lockEligibleViewCreateRequest(42L, 7L))
                .thenReturn(42L);
        when(departmentRequestMapper.selectVisibleViewCreateResult(42L, 7L))
                .thenReturn(new ViewCreateResultResponse());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> departmentRequestService.registerViewCreateResult(
                        42L, 7L, "COMPLETED", "수정된 결과", " "));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(departmentRequestMapper, never())
                .insertViewCreateResultHistory(42L, 7L, "");
        verify(departmentRequestMapper, never())
                .updateViewCreateResult(42L, 7L, "COMPLETED", "수정된 결과");
    }

    @Test
    void requesterDetailLoadsCurrentResultAndItsChangeHistory() {
        RequestDetailResponse request = new RequestDetailResponse();
        request.setRequestType("CREATE_VIEW");
        ViewCreateResultResponse current = new ViewCreateResultResponse();
        var history = new com.sqlflow.request.dto.ViewCreateResultHistoryResponse();

        when(requestMapper.selectMyRequestDetail(42L, 9L)).thenReturn(request);
        when(requestMapper.selectMyViewSqlItems(42L, 9L)).thenReturn(List.of());
        when(requestMapper.selectMyRequestApprovals(42L, 9L)).thenReturn(List.of());
        when(requestMapper.selectViewCreateResult(42L)).thenReturn(current);
        when(requestMapper.selectViewCreateResultHistory(42L)).thenReturn(List.of(history));

        RequestDetailResponse response = requestService.getMyRequestDetail(42L, 9L);

        assertSame(current, response.getViewCreateResult());
        assertSame(history, response.getViewCreateResultHistory().get(0));
    }

    private static RequestItemResponse sqlItem(int order, String sql) {
        RequestItemResponse item = new RequestItemResponse();
        item.setQueryOrder(order);
        item.setQueryTitle("SQL " + order);
        item.setSqlText(sql);
        return item;
    }
}
