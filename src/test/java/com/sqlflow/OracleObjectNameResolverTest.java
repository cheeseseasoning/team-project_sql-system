package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.service.OracleObjectNameResolver;

/**
 * 객체 이름의 스키마 분리와 대소문자 처리 규칙을 검증한다.
 * Oracle 접속이나 실제 객체 존재 여부 검사는 수행하지 않는다.
 */
class OracleObjectNameResolverTest {

    private final OracleObjectNameResolver resolver = new OracleObjectNameResolver();

    @Test
    void unqualifiedNameUsesCurrentSchema() {
        OracleObjectName result = resolver.resolve("USERS", "SQLFLOW");

        assertEquals("SQLFLOW", result.owner());
        assertEquals("USERS", result.objectName());
    }

    @Test
    void explicitSchemaOverridesCurrentSchema() {
        OracleObjectName result = resolver.resolve("HR.EMPLOYEES", "SQLFLOW");

        assertEquals("HR", result.owner());
        assertEquals("EMPLOYEES", result.objectName());
    }

    @Test
    void unquotedNamesAreConvertedToUppercase() {
        OracleObjectName result = resolver.resolve("sqlflow.users", "OTHER");

        assertEquals("SQLFLOW", result.owner());
        assertEquals("USERS", result.objectName());
    }

    @Test
    void quotedNamesPreserveCase() {
        OracleObjectName result = resolver.resolve("\"SqlFlow\".\"Users\"", "OTHER");

        assertEquals("SqlFlow", result.owner());
        assertEquals("Users", result.objectName());
    }

    @Test
    void dotInsideQuotedNameIsNotSchemaSeparator() {
        OracleObjectName result = resolver.resolve("\"Sales.Report\"", "SQLFLOW");

        assertEquals("SQLFLOW", result.owner());
        assertEquals("Sales.Report", result.objectName());
    }

    @Test
    void currentSchemaPreservesCase() {
        // DB에서 가져온 실제 스키마 이름은 임의로 대문자로 바꾸지 않는다.
        OracleObjectName result = resolver.resolve("users", "MixedCaseSchema");

        assertEquals("MixedCaseSchema", result.owner());
        assertEquals("USERS", result.objectName());
    }

    @Test
    void spacesAroundSchemaSeparatorAreAccepted() {
        OracleObjectName result = resolver.resolve(" sqlflow . users ", "OTHER");

        assertEquals("SQLFLOW", result.owner());
        assertEquals("USERS", result.objectName());
    }

    @Test
    void databaseLinkIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> resolver.resolve("USERS@REMOTE_DB", "SQLFLOW"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("지원하지 않는 조회 대상 이름 형식입니다.", exception.getReason());
    }

    @Test
    void nullNameIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> resolver.resolve(null, "SQLFLOW"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void blankNameIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> resolver.resolve("   ", "SQLFLOW"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void missingCurrentSchemaIsRejectedForUnqualifiedName() {
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve("USERS", null));
    }

    @Test
    void explicitSchemaDoesNotRequireCurrentSchema() {
        OracleObjectName result = resolver.resolve("SQLFLOW.USERS", null);

        assertEquals("SQLFLOW", result.owner());
        assertEquals("USERS", result.objectName());
    }
}