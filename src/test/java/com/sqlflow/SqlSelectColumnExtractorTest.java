package com.sqlflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlColumnQualifierValidator;
import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.SqlSelectColumnExtractor;

/**
 * 실제 SQL을 파싱해 SELECT 목록의 컬럼 추출을 검증한다.
 * Oracle 객체·컬럼 존재 여부와 다른 절의 컬럼은 검사하지 않는다.
 */
class SqlSelectColumnExtractorTest {

    private final SqlParserValidator parser = new SqlParserValidator();

    private final SqlSelectColumnExtractor extractor =
            new SqlSelectColumnExtractor(new SqlColumnQualifierValidator());

    @Test
    void multipleColumnNamesAreExtracted() {
        Set<String> result = extract(
                "SELECT USER_ID, USER_NAME FROM USERS");

        assertEquals(Set.of("USER_ID", "USER_NAME"), result);
    }

    @Test
    void tableAliasIsCheckedAndOutputAliasIsExcluded() {
        Set<String> result = extract(
                "SELECT U.USER_ID, U.USER_NAME AS NAME FROM USERS U");

        // U는 테이블 별칭, NAME은 출력 별칭이다. 실제 컬럼 이름만 반환한다.
        assertEquals(Set.of("USER_ID", "USER_NAME"), result);
    }

    @Test
    void duplicateColumnNamesAreCollectedOnce() {
        Set<String> result = extract(
                "SELECT U.USER_ID, U.USER_ID AS COPIED_ID FROM USERS U");

        assertEquals(Set.of("USER_ID"), result);
    }

    @Test
    void quotedColumnNameIsPreserved() {
        Set<String> result = extract(
                "SELECT U.\"UserName\" FROM USERS U");

        // 이후 Oracle 컬럼 검사에서 대소문자를 구분할 수 있도록 유지한다.
        assertEquals(Set.of("\"UserName\""), result);
    }

    @Test
    void unknownTableAliasIsRejected() {
        assertRejected("SELECT X.USER_NAME FROM USERS U");
    }

    @Test
    void wildcardIsRejectedUntilSupported() {
        assertRejected("SELECT * FROM USERS");
    }

    @Test
    void functionIsRejectedUntilSupported() {
        assertRejected("SELECT COUNT(*) FROM USERS");
    }

    @Test
    void calculationIsRejectedUntilSupported() {
        assertRejected("SELECT USER_ID + 1 FROM USERS");
    }

    @Test
    void joinIsRejectedUntilSupported() {
        assertRejected(
                "SELECT U.USER_NAME FROM USERS U "
                + "JOIN DEPARTMENT D ON D.DEPT_ID = U.DEPT_ID");
    }

    @Test
    void withQueryIsRejectedUntilSupported() {
        assertRejected(
                "WITH TARGET AS (SELECT USER_ID FROM USERS) "
                + "SELECT USER_ID FROM TARGET");
    }

    @Test
    void fromSubqueryIsRejectedUntilSupported() {
        assertRejected(
                "SELECT T.USER_ID FROM "
                + "(SELECT USER_ID FROM USERS) T");
    }

    @Test
    void unionIsRejectedUntilSupported() {
        assertRejected(
                "SELECT USER_ID FROM USERS "
                + "UNION ALL SELECT USER_ID FROM USERS");
    }

    @Test
    void nullSelectIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> extractor.extractColumns(null));
    }

    /** SQL을 실제로 파싱한 뒤 컬럼 추출기에 전달한다. */
    private Set<String> extract(String sql) {
        return extractor.extractColumns(parser.parseSelect(sql));
    }

    /** 파싱 성공 후 추출 단계에서 차단되는지 구분해 확인한다. */
    private void assertRejected(String sql) {
        var select = parser.parseSelect(sql);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> extractor.extractColumns(select));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}