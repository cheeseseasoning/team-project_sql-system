package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.request.dto.RequestItemResponse;

@ExtendWith(MockitoExtension.class)
class RequestSqlValidationServiceTest {

    private static final Long USER_ID = 10L;

    private static final String SALES_SQL =
            "SELECT * FROM BIZ_DATA.SALES";

    private static final String CUSTOMER_SQL =
            "SELECT * FROM BIZ_DATA.CUSTOMERS";

    private static final QueryObjectName SALES =
            new QueryObjectName("BIZ_DATA", "SALES");

    private static final QueryObjectName CUSTOMERS =
            new QueryObjectName("BIZ_DATA", "CUSTOMERS");

    @Mock
    private MySqlQueryMetadataValidationService metadataValidationService;

    @Mock
    private DataAccessValidationService dataAccessValidationService;

    @Spy
    private SqlParserValidator sqlParserValidator = new SqlParserValidator();

    @InjectMocks
    private RequestSqlValidationService service;

    @Test
    void validatesMetadataThenAccessForEverySql() {
        prepareMetadata(SALES_SQL, SALES);
        prepareMetadata(CUSTOMER_SQL, CUSTOMERS);

        service.validate(USER_ID, List.of(SALES_SQL, CUSTOMER_SQL));

        var order = inOrder(
                metadataValidationService,
                dataAccessValidationService);

        order.verify(metadataValidationService).validate(SALES_SQL);
        order.verify(dataAccessValidationService)
                .validate(USER_ID, Set.of(SALES));

        order.verify(metadataValidationService).validate(CUSTOMER_SQL);
        order.verify(dataAccessValidationService)
                .validate(USER_ID, Set.of(CUSTOMERS));
    }

    @Test
    void metadataFailureStopsBeforeAccessCheck() {
        when(metadataValidationService.validate(SALES_SQL))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "SQL 검증 실패"));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(USER_ID, List.of(SALES_SQL)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(dataAccessValidationService);
    }

    @Test
    void secondSqlAccessFailureRejectsTheWholeValidation() {
        prepareMetadata(SALES_SQL, SALES);
        prepareMetadata(CUSTOMER_SQL, CUSTOMERS);

        // 첫 번째 SQL은 접근 검사를 통과하고, 두 번째 SQL에서 차단되도록 한다.
        when(dataAccessValidationService.validate(USER_ID, Set.of(SALES)))
                .thenReturn(new DataAccessValidationService.ValidationResult("GENERAL", Set.of(1L)));

        when(dataAccessValidationService.validate(
                USER_ID, Set.of(CUSTOMERS)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "민감 조회 권한 없음"));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(
                        USER_ID, List.of(SALES_SQL, CUSTOMER_SQL)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());

        // 첫 SQL이 통과했더라도 두 번째 SQL의 실패를 그대로 전달한다.
        var order = inOrder(
                metadataValidationService,
                dataAccessValidationService);

        order.verify(metadataValidationService).validate(SALES_SQL);
        order.verify(dataAccessValidationService)
                .validate(USER_ID, Set.of(SALES));

        order.verify(metadataValidationService).validate(CUSTOMER_SQL);
        order.verify(dataAccessValidationService)
                .validate(USER_ID, Set.of(CUSTOMERS));
    }

    @Test
    void emptySqlListIsRejectedBeforeValidation() {
        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(USER_ID, List.of()));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());

        verifyNoInteractions(
                metadataValidationService,
                dataAccessValidationService);
    }

    @Test
    void validateItemsReturnsOneResultPerSqlAndKeepsFailureReason() {
        RequestItemResponse validItem = new RequestItemResponse();
        validItem.setQueryId(101L);
        validItem.setSqlText(SALES_SQL);
        RequestItemResponse invalidItem = new RequestItemResponse();
        invalidItem.setQueryId(102L);
        invalidItem.setSqlText(CUSTOMER_SQL);

        prepareMetadata(SALES_SQL, SALES);
        prepareMetadata(CUSTOMER_SQL, CUSTOMERS);
        when(dataAccessValidationService.validate(USER_ID, Set.of(SALES)))
                .thenReturn(new DataAccessValidationService.ValidationResult("GENERAL", Set.of(1L)));
        when(dataAccessValidationService.validate(USER_ID, Set.of(CUSTOMERS)))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "민감 데이터 조회 권한이 없습니다."));

        var results = service.validateItems(USER_ID, List.of(validItem, invalidItem));

        assertEquals(2, results.size());
        assertEquals("VALID", results.get(0).validationStatus());
        assertEquals("GENERAL", results.get(0).accessLevel());
        assertEquals(List.of(1L), results.get(0).resourceIds());
        assertEquals("INVALID", results.get(1).validationStatus());
        assertEquals("민감 데이터 조회 권한이 없습니다.", results.get(1).message());
        assertTrue(results.get(1).resourceIds().isEmpty());
    }

    @Test
    void invalidSyntaxIsReportedBeforeOpeningMySqlMetadataConnection() {
        RequestItemResponse item = new RequestItemResponse();
        item.setQueryId(103L);
        item.setSqlText("sql");

        var results = service.validateItems(USER_ID, List.of(item));

        assertEquals("INVALID", results.get(0).validationStatus());
        assertEquals("SQL 구문을 분석할 수 없습니다. 문법 또는 지원 여부를 확인하세요.",
                results.get(0).message());
        verifyNoInteractions(metadataValidationService, dataAccessValidationService);
    }

    @Test
    void metadataConnectionFailureReturnsAnActionableValidationFailure() {
        when(metadataValidationService.validate(SALES_SQL))
                .thenThrow(new CannotCreateTransactionException("connection timeout"));

        var error = assertThrows(
                ResponseStatusException.class,
                () -> service.validate(USER_ID, List.of(SALES_SQL)));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertTrue(error.getReason().contains("MySQL 메타데이터 DB에 연결할 수 없습니다"));
        verifyNoInteractions(dataAccessValidationService);
    }

    private void prepareMetadata(
            String sql,
            QueryObjectName target) {

        when(metadataValidationService.validate(sql))
                .thenReturn(
                        new MySqlQueryMetadataValidationService.ValidationResult(
                                Set.of(target),
                                Set.of(),
                                false));
    }
}
