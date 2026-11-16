package com.sqlflow.request.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlflow.request.service.CreateSqlTargetResolver.Kind;

class CreateSqlTargetResolverTest {
    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE VIEW demo.v AS SELECT 1;",
        "/* VIEW fake */ CREATE OR REPLACE VIEW demo.v AS SELECT 1;",
        "CREATE ALGORITHM=MERGE DEFINER='db_user'@'%' SQL SECURITY INVOKER VIEW demo.v AS SELECT 1;",
        "CREATE DEFINER=CURRENT_USER() VIEW demo.v (id) AS SELECT 1;",
        "-- comment\nCREATE VIEW demo.v AS SELECT unsupported_syntax;"
    })
    void recognizesViewHeadersWithoutDependingOnBodyParser(String sql) {
        var target = CreateSqlTargetResolver.resolve(sql);
        assertEquals(Kind.VIEW, target.kind());
        assertEquals("demo", target.schema());
        assertEquals("v", target.view());
    }
    @Test void quotedNamesAndUnqualifiedViewsArePreserved() {
        var target = CreateSqlTargetResolver.resolve("CREATE VIEW `데이터`.`v` AS SELECT 1;");
        assertEquals("데이터", target.schema());
        assertEquals("v", target.view());
        assertNull(CreateSqlTargetResolver.resolve("CREATE VIEW v AS SELECT 1;").schema());
    }
    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE TABLE t AS SELECT * FROM v;", "CREATE TEMPORARY TABLE t(id INT);",
        "CREATE UNIQUE INDEX i ON t(id);", "CREATE SPATIAL INDEX i ON t(point);",
        "CREATE SPATIAL REFERENCE SYSTEM 123 NAME test;", "CREATE UNDO TABLESPACE undo ADD DATAFILE 'undo.ibu';", "CREATE ROLE v;", "CREATE DATABASE demo;",
        "CREATE DEFINER='user'@'%' PROCEDURE p() BEGIN SELECT 'CREATE VIEW v'; END;",
        "CREATE FUNCTION f() RETURNS INT RETURN 1;", "CREATE EVENT e ON SCHEDULE EVERY 1 DAY DO SELECT 1;"
    })
    void doesNotTreatOtherCreateObjectsAsViews(String sql) {
        assertEquals(Kind.OTHER, CreateSqlTargetResolver.resolve(sql).kind());
    }
    @ParameterizedTest
    @ValueSource(strings = {"CREATE UNKNOWN x;", "CREATE VIEW;", "SELECT 'CREATE VIEW v';", "/*! CREATE VIEW v AS SELECT 1 */;"})
    void uncertainHeadersRemainUnknown(String sql) {
        assertEquals(Kind.UNKNOWN, CreateSqlTargetResolver.resolve(sql).kind());
    }
}
