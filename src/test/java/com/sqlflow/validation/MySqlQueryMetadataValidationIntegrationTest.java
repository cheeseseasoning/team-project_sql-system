package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;

/** 실제 DB 연결 환경에서만 실행한다. 업무 SQL 원문은 실행하지 않는다. */
@SpringBootTest
class MySqlQueryMetadataValidationIntegrationTest {
    @Autowired private MySqlQueryMetadataValidationService service;
    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT DISTINCT sale_id FROM sqlflow_demo.demo_sales LIMIT 10;",
        "WITH c AS (SELECT sale_id FROM sqlflow_demo.demo_sales) SELECT * FROM c;",
        "SELECT * FROM (SELECT sale_id FROM sqlflow_demo.demo_sales) c;",
        "SELECT sale_id FROM sqlflow_demo.demo_sales UNION SELECT sale_id FROM sqlflow_demo.demo_sales;"
    })
    void explainAndResourceExtractionAcceptComplexSelects(String sql) {
        assertEquals(Set.of(new QueryObjectName("sqlflow_demo", "demo_sales")), service.validate(sql).resources());
    }
    @Test void explainRejectsMissingColumn() {
        var error = assertThrows(ResponseStatusException.class,
                () -> service.validate("SELECT missing_test_column FROM sqlflow_demo.demo_sales;"));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}
