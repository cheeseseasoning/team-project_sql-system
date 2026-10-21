package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlTableExtractor;

import net.sf.jsqlparser.statement.select.Select;

/**
 * SQL 구조별 참조 테이블 추출 결과를 검증한다.
 * 테이블 이름의 실제 Oracle 존재 여부는 검사하지 않는다.
 */
class SqlTableExtractorTest {

    private final SqlParserValidator validator = new SqlParserValidator();
    private final SqlTableExtractor extractor = new SqlTableExtractor();

    @Test
    void singleTableIsExtracted() {
        Set<String> tables = extract("SELECT USER_NAME FROM USERS");

        assertEquals(Set.of("USERS"), tables);
    }

    @Test
    void joinedTablesAreExtractedWithoutAliases() {
        Set<String> tables = extract("""
                SELECT U.USER_NAME, D.DEPT_NAME
                FROM USERS U
                JOIN DEPARTMENT D ON D.DEPT_ID = U.DEPT_ID
                """);

        // 별칭 U, D가 아니라 실제 참조 객체 이름을 반환해야 한다.
        assertEquals(Set.of("USERS", "DEPARTMENT"), tables);
    }

    @Test
    void tableInsideSubqueryIsExtracted() {
        Set<String> tables = extract("""
                SELECT U.USER_NAME
                FROM USERS U
                WHERE U.DEPT_ID IN (
                    SELECT D.DEPT_ID
                    FROM DEPARTMENT D
                )
                """);

        assertEquals(Set.of("USERS", "DEPARTMENT"), tables);
    }

    @Test
    void withQueryReturnsUnderlyingTable() {
        Set<String> tables = extract("""
                WITH USER_LIST AS (
                    SELECT USER_ID, USER_NAME
                    FROM USERS
                )
                SELECT USER_NAME
                FROM USER_LIST
                """);

        // USER_LIST는 SQL 안에서 정의한 이름이므로 DB 테이블로 반환하면 안 된다.
        assertEquals(Set.of("USERS"), tables);
    }

    @Test
    void repeatedTableIsReturnedOnce() {
        Set<String> tables = extract("""
                SELECT A.USER_NAME, B.USER_NAME
                FROM USERS A
                JOIN USERS B ON A.DEPT_ID = B.DEPT_ID
                """);

        assertEquals(Set.of("USERS"), tables);
    }

    @Test
    void schemaNameIsPreserved() {
        Set<String> tables = extract("SELECT USER_NAME FROM SQLFLOW.USERS");

        // 다른 스키마의 동명 테이블과 구분하기 위해 스키마를 유지한다.
        assertEquals(Set.of("SQLFLOW.USERS"), tables);
    }

    @Test
    void nullSelectIsRejected() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> extractor.extractTables(null));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("분석된 SELECT 정보가 없습니다.", exception.getReason());
    }

    /** SQL 문자열을 파싱한 뒤 테이블 추출기에 전달한다. */
    private Set<String> extract(String sqlText) {
        Select select = validator.parseSelect(sqlText);

        return extractor.extractTables(select);
    }
}