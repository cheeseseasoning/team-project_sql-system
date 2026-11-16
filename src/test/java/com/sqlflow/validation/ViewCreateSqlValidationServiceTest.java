package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.service.ViewCreateSqlValidationService;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;

class ViewCreateSqlValidationServiceTest {
    private final ViewCreateSqlValidationService service = new ViewCreateSqlValidationService(new SqlBasicValidator());
    @Test void allCreateFormsPassBasicFormatAndStillRequireDbTeamReview() {
        assertEquals(Status.VALID, service.validate("CREATE TABLE t(id INT);").getStatus());
        assertEquals(Status.VALID, service.validate("CREATE OR REPLACE VIEW v AS SELECT * FROM t;").getStatus());
        assertEquals(Status.VALID, service.validate("CREATE PROCEDURE p() BEGIN SELECT 1; END;").getStatus());
    }
    @Test void blocksWrongTypeMultipleStatementsAndMissingTerminator() {
        assertEquals(Status.BLOCKED, service.validate("DROP TABLE t;").getStatus());
        assertEquals(Status.BLOCKED, service.validate("CREATE TABLE t(id INT); DELETE FROM t;").getStatus());
        assertEquals(Status.BLOCKED, service.validate("CREATE TABLE t(id INT)").getStatus());
    }
}
