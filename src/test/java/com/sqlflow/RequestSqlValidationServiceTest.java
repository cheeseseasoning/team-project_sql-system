package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.access.service.RequestSqlValidationService;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;

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
