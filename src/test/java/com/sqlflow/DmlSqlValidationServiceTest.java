package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService.SqlType;
import com.sqlflow.request.service.DmlSqlValidationService.Status;

class DmlSqlValidationServiceTest {

    private final DmlSqlValidationService service = new DmlSqlValidationService();

    @Test
    void 정상_변경문은_통과한다() {
        assertEquals(Status.VALID,
                service.validate(SqlType.INSERT, "INSERT INTO demo_product (product_id) VALUES (1)").status());
        assertEquals(Status.VALID,
                service.validate(SqlType.UPDATE, "UPDATE demo_product SET product_name = '테스트' WHERE product_id = 1").status());
        assertEquals(Status.VALID,
                service.validate(SqlType.DELETE, "DELETE FROM demo_product WHERE product_id = 1").status());
    }

    @Test
    void 조회문과_여러_문장은_차단한다() {
        assertEquals(Status.BLOCKED,
                service.validate(SqlType.DELETE, "SELECT * FROM demo_product").status());
        assertEquals(Status.BLOCKED,
                service.validate(SqlType.DELETE, "DELETE FROM demo_product WHERE product_id = 1; SELECT 1;").status());
    }

    @Test
    void 선택한_유형과_다른_변경문은_차단한다() {
        assertEquals(Status.BLOCKED,
                service.validate(SqlType.UPDATE, "DELETE FROM demo_product WHERE product_id = 1").status());
    }

    @Test
    void 파싱에_실패하면_경고한다() {
        assertEquals(Status.WARNING,
                service.validate(SqlType.UPDATE, "UPDATE demo_product SET product_name =").status());
    }
}
