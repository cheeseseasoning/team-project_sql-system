package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlParserValidator;

import net.sf.jsqlparser.statement.select.Select;

/**
 * SQL 파싱과 단일 SELECT 검사를 검증한다.
 * Spring을 실행하거나 Oracle DB에 연결하지 않는다.
 */
class SqlParserValidatorTest {

    private final SqlParserValidator validator = new SqlParserValidator();

    @Test
    void singleSelectIsAccepted() {
        Select result = validator.parseSelect("SELECT USER_NAME FROM USERS");

        assertNotNull(result);
    }

    @Test
    void lowercaseSelectWithTrailingSemicolonIsAccepted() {
        // 마지막 세미콜론은 문장 구분자이며, 이것만으로 두 문장이 되지는 않는다.
        Select result = validator.parseSelect("select 1 from dual;");

        assertNotNull(result);
    }

    @Test
    void nullSqlIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.parseSelect(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("검증할 SQL이 없습니다.", exception.getReason());
    }

    @Test
    void blankSqlIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.parseSelect("   "));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("검증할 SQL이 없습니다.", exception.getReason());
    }

    @Test
    void invalidSyntaxIsRejected() {
        // FROM 뒤에 조회 대상이 없어 파싱에 실패해야 한다.
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.parseSelect("SELECT * FROM"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("SQL 구문을 분석할 수 없습니다. 문법 또는 지원 여부를 확인하세요.", exception.getReason());
    }

    @Test
    void deleteStatementIsRejected() {
        // 문법이 올바르더라도 SELECT가 아닌 문장은 허용하지 않는다.
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.parseSelect("DELETE FROM USERS"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("조회용 SELECT 문만 허용합니다.", exception.getReason());
    }

    @Test
    void multipleStatementsAreRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> validator.parseSelect("SELECT 1 FROM DUAL; SELECT SYSDATE FROM DUAL;"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("SQL 항목에는 문장 하나만 등록할 수 있습니다.", exception.getReason());
    }
}