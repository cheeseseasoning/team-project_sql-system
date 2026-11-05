package com.sqlflow.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import com.sqlflow.request.mapper.DepartmentRequestMapper;
import com.sqlflow.request.mapper.RequestMapper;
import com.sqlflow.request.service.ApprovalLineCreator;
import com.sqlflow.request.service.DepartmentRequestService;
import com.sqlflow.request.service.RequestService;
import com.sqlflow.request.service.DmlSqlValidationService;
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
                new ViewCreateSqlValidationService(),
                new DmlSqlValidationService());

        departmentRequestService =
                new DepartmentRequestService(departmentRequestMapper);
    }

    @Test
    void downloadAddsOneTerminatorOnlyWhenSqlDoesNotAlreadyHaveOne() {
        RequestItemResponse first = sqlItem(
                1, "CREATE VIEW v_one AS SELECT 1 FROM DUAL");
        RequestItemResponse second = sqlItem(
                2, "CREATE VIEW v_two AS SELECT 2 FROM DUAL;  \n");

        // 공통 조회가 승인과 권한을 확인하고 SQL 두 건을 반환했다고 가정한다.
        when(requestMapper.selectApprovedChangeSqlItems(42L, 7L))
                .thenReturn(List.of(first, second));

        String file = new String(
                requestService.downloadApprovedViewSql(42L, 7L),
                StandardCharsets.UTF_8);

        // 첫 SQL에만 세미콜론을 추가하고, 둘째 SQL의 세미콜론은 그대로 쓴다.
        assertTrue(file.contains(
                "CREATE VIEW v_one AS SELECT 1 FROM DUAL\n;\n\n-- SQL 2"));
        assertTrue(file.contains(
                "CREATE VIEW v_two AS SELECT 2 FROM DUAL;\n\n"));
        assertEquals(2, file.chars().filter(ch -> ch == ';').count());
    }

    @Test
    void firstExternalResultDoesNotRequireAChangeReasonOrCreateHistory() {
        when(departmentRequestMapper.insertChangeSqlResult(42L, 16L, 7L, "COMPLETED", null, 8L))
                .thenReturn(1);

        departmentRequestService.registerChangeSqlResult(42L, 16L, 7L, "COMPLETED", null, 8L);

        verify(departmentRequestMapper).insertChangeSqlResult(42L, 16L, 7L, "COMPLETED", null, 8L);
        verifyNoMoreInteractions(departmentRequestMapper);
    }

    @Test
    void changingExternalResultArchivesPreviousValueBeforeUpdating() {
        when(departmentRequestMapper.lockChangeSqlResult(42L, 16L, 7L)).thenReturn("FAILED");
        when(departmentRequestMapper.insertChangeSqlResultHistory(16L, 7L, "CORRECTION", "실행 로그 확인"))
                .thenReturn(1);
        when(departmentRequestMapper.updateChangeSqlResult(16L, 7L, "COMPLETED", null, 8L))
                .thenReturn(1);

        departmentRequestService.changeSqlResult(
                42L, 16L, 7L, "COMPLETED", null, 8L, "CORRECTION", " 실행 로그 확인 ");

        InOrder order = inOrder(departmentRequestMapper);
        order.verify(departmentRequestMapper).lockChangeSqlResult(42L, 16L, 7L);
        order.verify(departmentRequestMapper).insertChangeSqlResultHistory(16L, 7L, "CORRECTION", "실행 로그 확인");
        order.verify(departmentRequestMapper).updateChangeSqlResult(16L, 7L, "COMPLETED", null, 8L);
    }

    @Test
    void changingExternalResultWithoutReasonIsRejectedBeforeWritingHistory() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> departmentRequestService.changeSqlResult(
                        42L, 16L, 7L, "COMPLETED", null, 8L, "CORRECTION", " "));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(departmentRequestMapper);
    }

    @Test
    void requesterDetailReturnsCurrentResultWithItsSqlItem() {
        RequestDetailResponse request = new RequestDetailResponse();
        request.setRequestType("CREATE_VIEW");
        RequestItemResponse item = sqlItem(1, "CREATE VIEW v_one AS SELECT 1 FROM DUAL");
        item.setChangeResultStatus("COMPLETED");

        when(requestMapper.selectMyRequestDetail(42L, 9L)).thenReturn(request);
        when(requestMapper.selectMyViewSqlItems(42L, 9L)).thenReturn(List.of(item));
        when(requestMapper.selectMyRequestApprovals(42L, 9L)).thenReturn(List.of());

        RequestDetailResponse response = requestService.getMyRequestDetail(42L, 9L);

        assertSame(item, response.getItems().get(0));
        assertEquals("COMPLETED", response.getItems().get(0).getChangeResultStatus());
    }

    private static RequestItemResponse sqlItem(int order, String sql) {
        RequestItemResponse item = new RequestItemResponse();
        item.setQueryOrder(order);
        item.setQueryTitle("SQL " + order);
        item.setSqlText(sql);
        return item;
    }
}
