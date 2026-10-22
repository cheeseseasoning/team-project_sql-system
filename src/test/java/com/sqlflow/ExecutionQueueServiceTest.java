package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionQueueDetailResponse;
import com.sqlflow.execution.dto.ExecutionQueueSqlResponse;
import com.sqlflow.execution.mapper.ExecutionQueueMapper;
import com.sqlflow.execution.service.ExecutionQueueService;

@ExtendWith(MockitoExtension.class)
class ExecutionQueueServiceTest {
    @Mock private ExecutionQueueMapper mapper;
    @InjectMocks private ExecutionQueueService service;

    @Test
    void nonDbApproverCannotReadQueue() {
        SqlFlowUserDetails user = user(List.of());
        when(mapper.isActiveDbApprover(10L)).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getQueue(user, 1, 10, null));
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(mapper, never()).selectReadyRequests(anyString(), anyInt(), anyInt());
    }

    @Test
    void detailReturnsStoredSqlOnlyAfterCurrentRoleCheck() {
        when(mapper.isActiveDbApprover(10L)).thenReturn(true);
        ExecutionQueueDetailResponse detail = new ExecutionQueueDetailResponse();
        detail.setRequestId(20L);
        var sql = new ExecutionQueueSqlResponse();
        sql.setSqlText("SELECT 1");
        when(mapper.selectRequestDetail(20L)).thenReturn(detail);
        when(mapper.selectRequestSqlItems(20L)).thenReturn(List.of(sql));

        var actual = service.getDetail(user(List.of("DB_APPROVER")), 20L);

        assertEquals("SELECT 1", actual.getSqlItems().get(0).getSqlText());
    }

    private SqlFlowUserDetails user(List<String> roles) {
        UserAccount account = new UserAccount();
        account.setUserId(10L); account.setEnabled("Y"); account.setLoginId("test");
        return new SqlFlowUserDetails(account, roles);
    }
}
