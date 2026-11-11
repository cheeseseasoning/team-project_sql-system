package com.sqlflow.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.schedule.mapper.DepartmentScheduleMapper;
import com.sqlflow.schedule.service.DepartmentScheduleService;

class DepartmentScheduleServiceTest {
    private final DepartmentScheduleMapper mapper = mock(DepartmentScheduleMapper.class);
    private final DepartmentScheduleService service = new DepartmentScheduleService(mapper);

    @Test
    void deleteUsesAuthenticatedDepartmentAndCreator() {
        var user = mock(SqlFlowUserDetails.class);
        when(user.getDeptId()).thenReturn(3L);
        when(user.getUserId()).thenReturn(7L);
        when(mapper.deleteOwned(12L, 3L, 7L)).thenReturn(1);

        service.delete(user, 12L);

        verify(mapper).deleteOwned(12L, 3L, 7L);
    }

    @Test
    void missingOrUnownedScheduleIsRejected() {
        var user = mock(SqlFlowUserDetails.class);
        when(user.getDeptId()).thenReturn(3L);
        when(user.getUserId()).thenReturn(7L);

        var error = assertThrows(ResponseStatusException.class, () -> service.delete(user, 12L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void unauthenticatedDeleteDoesNotReachDatabase() {
        var error = assertThrows(ResponseStatusException.class, () -> service.delete(null, 12L));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(mapper);
    }
}
