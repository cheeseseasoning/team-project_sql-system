package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import com.sqlflow.request.service.ViewCreateSqlValidationService;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;
import com.sqlflow.execution.service.SqlBasicValidator;

class SqlBasicValidatorTest {
    private final SqlBasicValidator validator = new SqlBasicValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT ';' FROM users;", "/* leading ; */ SELECT * FROM users;",
        "SELECT * FROM users WHERE name = 'it''s;ok';", "SELECT * FROM users; -- comment\n;",
        "SELECT * FROM users; DELETE FROM users;", "SELECT * FROM users;;"
    })
    void quotedAndCommentSemicolonsDoNotHideMultipleStatements(String sql) {
        boolean multiple = sql.contains("DELETE") || sql.contains(";;") || sql.contains("-- comment");
        assertEquals(multiple, validator.validate("SELECT", sql).blocked());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE DEFINER = CURRENT_USER() PROCEDURE p() BEGIN SELECT 1; END;",
        "CREATE EVENT e ON SCHEDULE EVERY 1 DAY DO IF (1) THEN DELETE FROM t; END IF;",
        "CREATE TABLE t (id INT);", "CREATE OR REPLACE VIEW v AS SELECT * FROM t;",
        "CREATE INDEX idx ON t(id);", "CREATE USER 'reviewer'@'localhost';",
        "CREATE ROLE reviewer;", "CREATE DATABASE demo;",
        "CREATE PROCEDURE p() BEGIN SELECT 1; SELECT 2; END;",
        "CREATE FUNCTION f() RETURNS INT BEGIN IF 1 THEN RETURN 1; END IF; RETURN 0; END;",
        "CREATE TRIGGER tr BEFORE INSERT ON t FOR EACH ROW BEGIN SET NEW.id = 1; END;",
        "CREATE EVENT e ON SCHEDULE EVERY 1 DAY DO BEGIN DELETE FROM t; END;"
    })
    void acceptsCreateTypesAndRoutineInternalTerminators(String sql) {
        assertFalse(validator.validate("CREATE", sql).blocked());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT * FROM t", "SELECT * FROM t; -- after", "SELECT * FROM t; -- ;", "SELECT * FROM t; /* ; */ ;", "DELETE FROM t;",
        "SELECT 'unterminated;", "/*!50000 DELETE FROM t */ SELECT * FROM t;",
        "SELECT * FROM t INTO OUTFILE '/tmp/test';", "SELECT * FROM t FOR UPDATE;"})
    void rejectsMissingTerminatorWrongTypeAndNonReadOnlySelect(String sql) {
        assertTrue(validator.validate("SELECT", sql).blocked());
    }

    @Test
    void findsActualCommandAfterCte() {
        assertFalse(validator.validate("SELECT", "WITH c AS (SELECT * FROM t) SELECT * FROM c;").blocked());
        assertFalse(validator.validate("UPDATE", "WITH c AS (SELECT * FROM t) UPDATE t SET id=1;").blocked());
        assertTrue(validator.validate("SELECT", "WITH c AS (SELECT * FROM t) DELETE FROM t;").blocked());
    }

    @Test
    void blocksAnotherStatementAfterRoutine() {
        assertTrue(validator.validate("CREATE", "CREATE PROCEDURE p() BEGIN SELECT 1; END; DROP TABLE t;").blocked());
        assertTrue(validator.validate("CREATE", "CREATE FUNCTION f() RETURNS INT RETURN IF(1, 1, 0); SELECT 1;").blocked());
        assertTrue(validator.validate("CREATE", "CREATE FUNCTION IF NOT EXISTS f() RETURNS INT RETURN 1; SELECT 1;").blocked());
    }

    @Test
    void doesNotLimitSqlLength() {
        assertFalse(validator.validate("SELECT", "SELECT '" + "a".repeat(25000) + "' FROM t;").blocked());
    }
    /** 정상 제어문은 경고 없이 허용하고, 종료 뒤에 추가된 별도 문장은 차단한다. */
    @ParameterizedTest
    @MethodSource("balancedControlRoutines")
    void closedControlsDoNotHideAnotherTopLevelStatement(String sql) {
        var valid = validator.validate("CREATE", sql);
        assertFalse(valid.blocked());
        assertNull(valid.warning());
        var extra = validator.validate("CREATE", sql + " SELECT 2;");
        assertTrue(extra.blocked(), "복합 CREATE가 끝난 뒤의 SELECT는 별도 문장이다.");

        var service = new ViewCreateSqlValidationService(validator);
        assertEquals(Status.VALID, service.validate(sql).getStatus());
        assertEquals(Status.BLOCKED, service.validate(sql + " SELECT 2;").getStatus());
    }

    static java.util.stream.Stream<String> balancedControlRoutines() {
        return java.util.stream.Stream.of(
                "CREATE PROCEDURE p() BEGIN IF (1) THEN SELECT 1; END IF; END;",
                "CREATE FUNCTION f() RETURNS INT BEGIN IF (1) THEN RETURN 1; END IF; RETURN 0; END;",
                "CREATE EVENT e ON SCHEDULE EVERY 1 DAY DO IF (1) THEN SELECT 1; END IF;",
                "CREATE PROCEDURE p() BEGIN WHILE (1) DO SET @x=1; END WHILE; END;",
                "CREATE PROCEDURE p() BEGIN outer_loop: LOOP LEAVE outer_loop; END LOOP outer_loop; END;",
                "CREATE PROCEDURE p() BEGIN REPEAT SET @x=1; UNTIL @x=1 END REPEAT; END;",
                "CREATE PROCEDURE p() BEGIN IF (1) THEN IF (2) THEN SELECT 1; END IF; END IF; END;",
                "CREATE PROCEDURE p() BEGIN CASE WHEN 1 THEN IF (1) THEN SELECT 1; END IF; END CASE; END;",
                "CREATE PROCEDURE p() BEGIN IF (1) THEN BEGIN SELECT 1; END; END IF; END;",
                "CREATE FUNCTION f() RETURNS INT RETURN IF(1,1,0);",
                "CREATE FUNCTION f() RETURNS VARCHAR(10) RETURN REPEAT('a',2);",
                "CREATE PROCEDURE IF NOT EXISTS p() BEGIN IF (1) THEN SELECT 'END IF;'; END IF; END;");
    }

    @Test
    void unresolvedControlStillRequiresReviewInsteadOfBeingTreatedAsValidated() {
        var result = validator.validate("CREATE", "CREATE PROCEDURE p() BEGIN IF (1) THEN SELECT 1; END;");
        assertFalse(result.blocked());
        assertNotNull(result.warning());
    }

    @Test
    void selectFailureDoesNotReferToATypeDropdown() {
        var explain = validator.validate("SELECT", "EXPLAIN ANALYZE SELECT * FROM t;");
        assertTrue(explain.blocked());
        assertTrue(explain.warning().contains("EXPLAIN 또는 EXPLAIN ANALYZE를 제거"));
        assertFalse(explain.warning().contains("선택한"));
        var dml = validator.validate("SELECT", "DELETE FROM t;");
        assertTrue(dml.blocked());
        assertTrue(dml.warning().contains("데이터 변경 요청"));
        assertFalse(dml.warning().contains("선택한"));
        var wrongDml = validator.validate("INSERT", "UPDATE t SET id=1;");
        assertTrue(wrongDml.blocked());
        assertTrue(wrongDml.warning().contains("선택한 유형은 INSERT"));
    }
}
