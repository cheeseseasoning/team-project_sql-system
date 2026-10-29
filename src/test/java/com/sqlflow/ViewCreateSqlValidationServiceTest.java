package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;
import com.sqlflow.request.service.ViewCreateSqlValidationService;

/**
 * CREATE VIEW 기본 검사 정책을 확인한다.
 * Spring 서버를 실행하거나 실제 DB에 연결하지 않는다.
 */
class ViewCreateSqlValidationServiceTest {

    private final ViewCreateSqlValidationService service =
            new ViewCreateSqlValidationService();

    @Test
    void validCreateViewPasses() {
        // 테이블의 실제 존재 여부는 검사하지 않는다.
        var result = service.validate("""
                CREATE VIEW v_product AS
                SELECT product_id, product_name
                FROM demo_product
                """);

        assertEquals(Status.VALID, result.getStatus());
    }

    @Test
    void lowercaseWithTrailingSemicolonPasses() {
        var result = service.validate(
                "  create view v_test as select 1 as id;  ");

        assertEquals(Status.VALID, result.getStatus());
    }

    @Test
    void emptyInputIsBlocked() {
        assertEquals(
                Status.BLOCKED,
                service.validate(null).getStatus());

        assertEquals(
                Status.BLOCKED,
                service.validate("   ").getStatus());
    }

    @Test
    void otherCommandsAreBlocked() {
        assertEquals(
                Status.BLOCKED,
                service.validate("SELECT 1").getStatus());

        assertEquals(
                Status.BLOCKED,
                service.validate("DROP VIEW v_test").getStatus());
    }

    @Test
    void replacingExistingViewIsBlocked() {
        var result = service.validate(
                "CREATE OR REPLACE VIEW v_test AS SELECT 1 AS id");

        assertEquals(Status.BLOCKED, result.getStatus());
    }

    @Test
    void multipleStatementsAreBlocked() {
        var result = service.validate("""
                CREATE VIEW v_test AS SELECT 1 AS id;
                DROP VIEW v_test;
                """);

        assertEquals(Status.BLOCKED, result.getStatus());
    }

    @Test
    void parserFailureReturnsWarning() {
        // 파싱 실패를 확정적인 문법 오류로 단정하지 않는다.
        var result = service.validate(
                "CREATE VIEW v_test AS SELECT * FROM");

        assertEquals(Status.WARNING, result.getStatus());
        assertFalse(result.getMessage().isBlank());
    }

    @Test
    void semicolonInsideStringIsBlockedByCurrentInputPolicy() {
        // 현재 기본 범위에서는 문자열 내부 세미콜론도 지원하지 않는다.
        var result = service.validate(
                "CREATE VIEW v_test AS SELECT 'a;b' AS label");

        assertEquals(Status.BLOCKED, result.getStatus());
    }
}