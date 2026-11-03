package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService.Status;

class DmlSqlValidationServiceTest {

    private final DmlSqlValidationService service = new DmlSqlValidationService();

    @Test
    void 정상_변경문은_통과한다() {
        assertEquals(Status.VALID,
                service.validate("INSERT INTO demo_product (product_id) VALUES (1)").status());
        assertEquals(Status.VALID,
                service.validate("UPDATE demo_product SET product_name = '테스트' WHERE product_id = 1").status());
        assertEquals(Status.VALID,
                service.validate("DELETE FROM demo_product WHERE product_id = 1").status());
    }

    @Test
    void 조회문과_여러_문장은_차단한다() {
        assertEquals(Status.BLOCKED,
                service.validate("SELECT * FROM demo_product").status());
        assertEquals(Status.BLOCKED,
                service.validate("DELETE FROM demo_product WHERE product_id = 1; SELECT 1;").status());
    }

    @Test
    void 파싱에_실패하면_경고한다() {
        assertEquals(Status.WARNING,
                service.validate("UPDATE demo_product SET product_name =").status());
    }
}