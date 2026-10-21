package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlColumnQualifierValidator;
import com.sqlflow.execution.service.SqlParserValidator;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;

/**
 * 실제 SQL을 파싱해 단일 테이블의 컬럼 접두어를 검사한다.
 * Oracle 객체·컬럼 존재 여부와 DB 접근 권한은 검사하지 않는다.
 */
class SqlColumnQualifierValidatorTest {

    private final SqlParserValidator parser = new SqlParserValidator();
    private final SqlColumnQualifierValidator validator =
            new SqlColumnQualifierValidator();

    @Test
    void columnWithoutQualifierIsAccepted() {
        assertDoesNotThrow(
                () -> validate("SELECT USER_NAME FROM USERS"));
    }

    @Test
    void matchingTableNameIsAccepted() {
        assertDoesNotThrow(
                () -> validate("SELECT USERS.USER_NAME FROM USERS"));
    }

    @Test
    void matchingAliasIsAccepted() {
        assertDoesNotThrow(
                () -> validate("SELECT U.USER_NAME FROM USERS U"));
    }

    @Test
    void unquotedAliasIsCaseInsensitive() {
        assertDoesNotThrow(
                () -> validate("SELECT u.USER_NAME FROM USERS U"));
    }

    @Test
    void unknownAliasIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validate("SELECT X.USER_NAME FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "FROM 대상과 일치하지 않는 컬럼 접두어입니다: X",
                exception.getReason());
    }

    @Test
    void originalTableNameIsRejectedWhenAliasIsDefined() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validate("SELECT USERS.USER_NAME FROM USERS U"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void matchingQuotedAliasIsAccepted() {
        assertDoesNotThrow(
                () -> validate("SELECT \"u\".USER_NAME FROM USERS \"u\""));
    }

    @Test
    void quotedAliasWithDifferentCaseIsRejected() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validate("SELECT \"U\".USER_NAME FROM USERS \"u\""));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void aliasIsAcceptedWhenFromTableHasSchema() {
    	// FROM의 스키마 지정과 컬럼 쪽의 별칭 참조는 별도로 처리한다.
        assertDoesNotThrow(
                () -> validate("SELECT U.USER_NAME FROM SQLFLOW.USERS U"));
    }

    @Test
    void schemaQualifiedColumnIsRejectedAsUnsupported() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> validate(
                        "SELECT SQLFLOW.USERS.USER_NAME FROM SQLFLOW.USERS"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(
                "스키마를 포함한 컬럼 접두어는 아직 지원하지 않습니다.",
                exception.getReason());
    }

    @Test
    void nullColumnIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateQualifier(null, new Table("USERS")));
    }

    @Test
    void nullFromTableIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateQualifier(new Column("USER_NAME"), null));
    }

    /** 테스트 SQL의 첫 번째 컬럼과 FROM 테이블을 꺼내 검사한다. */
    private void validate(String sql) {
        PlainSelect select = assertInstanceOf(
                PlainSelect.class, parser.parseSelect(sql));

        Table fromTable = assertInstanceOf(
                Table.class, select.getFromItem());

        Column column = assertInstanceOf(
                Column.class, select.getSelectItems().get(0).getExpression());

        validator.validateQualifier(column, fromTable);
    }
}