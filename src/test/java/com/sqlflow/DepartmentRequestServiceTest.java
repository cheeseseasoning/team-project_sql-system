package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.request.dto.DepartmentRequestListResponse;
import com.sqlflow.request.dto.RequestApprovalResponse;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.mapper.DepartmentRequestMapper;
import com.sqlflow.request.service.DepartmentRequestService;

@ExtendWith(MockitoExtension.class)
class DepartmentRequestServiceTest {

    @Mock
    private DepartmentRequestMapper mapper;

    @InjectMocks
    private DepartmentRequestService service;

    @Test
    void normalizesSearchAndClampsToLastPage() {
        var item = new DepartmentRequestListResponse();
        item.setRequestId(100L);

        when(mapper.countVisibleRequests(1L, "매출", "READY"))
                .thenReturn(21L);

        when(mapper.selectVisibleRequestsPage(
                1L, "매출", "READY", 20L, 10))
                .thenReturn(List.of(item));

        var response = service.getRequestsPage(
                1L, " 매출 ", " READY ", 99, 10);

        assertEquals(3, response.page());
        assertEquals(3L, response.totalPages());
        assertEquals(21L, response.totalCount());
        assertEquals(100L, response.items().get(0).getRequestId());
    }

    @Test
    void emptyResultDoesNotQueryPageItems() {
        when(mapper.countVisibleRequests(1L, "", ""))
                .thenReturn(0L);

        var response = service.getRequestsPage(
                1L, null, null, 99, 10);

        assertEquals(1, response.page());
        assertEquals(0L, response.totalCount());
        assertEquals(0L, response.totalPages());
        assertTrue(response.items().isEmpty());

        verify(mapper).countVisibleRequests(1L, "", "");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingActorIsRejectedBeforeQuery() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestsPage(null, "", "", 1, 10));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(mapper);
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void invalidQueryIsRejectedBeforeDatabaseAccess(
            String keyword,
            String status,
            int page,
            int size) {

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestsPage(
                        1L, keyword, status, page, size));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(mapper);
    }
    
    @Test
    void detailContainsItemsAndApprovals() {
        var detail = new RequestDetailResponse();
        detail.setRequestId(100L);

        var item = new RequestItemResponse();
        item.setSqlText("SELECT 1 FROM DUAL");

        var approval = new RequestApprovalResponse();
        approval.setApprovalOrder(1);

        when(mapper.selectVisibleRequestDetail(100L, 1L))
                .thenReturn(detail);

        when(mapper.selectVisibleRequestItems(100L, 1L))
                .thenReturn(List.of(item));

        when(mapper.selectVisibleRequestApprovals(100L, 1L))
                .thenReturn(List.of(approval));

        var response = service.getRequestDetail(100L, 1L);

        assertSame(detail, response);
        assertSame(item, response.getItems().get(0));
        assertSame(approval, response.getApprovals().get(0));
    }

    @Test
    void inaccessibleDetailDoesNotLoadItemsOrApprovals() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestDetail(100L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(mapper).selectVisibleRequestDetail(100L, 1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void invalidDetailRequestIdIsRejectedBeforeQuery() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestDetail(0L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(mapper);
    }

    @Test
    void detailRequiresAuthenticatedActor() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestDetail(100L, null));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatusCode());
        verifyNoInteractions(mapper);
    }
    
    @Test
    void visibleRequestHistoryIsReturned() {
        var history = new ApprovalHistoryResponse();
        history.setApproverName("테스트 결재자");

        when(mapper.countVisibleRequest(100L, 1L))
                .thenReturn(1);

        when(mapper.selectVisibleRequestApprovalHistory(100L, 1L))
                .thenReturn(List.of(history));

        var response = service.getRequestApprovalHistory(100L, 1L);

        assertSame(history, response.get(0));
    }

    @Test
    void inaccessibleHistoryIsNotLoaded() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.getRequestApprovalHistory(100L, 1L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());

        verify(mapper).countVisibleRequest(100L, 1L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void historyRejectsInvalidIdsBeforeQuery() {
        assertEquals(
                HttpStatus.UNAUTHORIZED,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getRequestApprovalHistory(100L, null))
                        .getStatusCode());

        assertEquals(
                HttpStatus.BAD_REQUEST,
                assertThrows(
                        ResponseStatusException.class,
                        () -> service.getRequestApprovalHistory(0L, 1L))
                        .getStatusCode());

        verifyNoInteractions(mapper);
    }

    private static Stream<Arguments> invalidQueries() {
        return Stream.of(
                Arguments.of("x".repeat(121), "", 1, 10),
                Arguments.of("", "UNKNOWN", 1, 10),
                Arguments.of("", "", 0, 10),
                Arguments.of("", "", 1, 0),
                Arguments.of("", "", 1, 51));
    }
}