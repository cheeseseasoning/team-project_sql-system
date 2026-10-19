package com.sqlflow.execution.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;

/**
 * SQL을 파싱하고 단일 SELECT 문인지 확인한다.
 * 파싱 결과는 이후 참조 테이블·컬럼 분석에 사용한다.
 */
@Component
public class SqlParserValidator {

    /**
     * 입력 SQL을 분석하여 SELECT 구조를 반환한다.
     * SQL을 DB에서 실행하거나 원문을 변경하지 않는다.
     */
    public Select parseSelect(String sqlText) {

        if (sqlText == null || sqlText.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검증할 SQL이 없습니다.");
        }

        Statements parsed;

        try {
            parsed = CCJSqlParserUtil.parseStatements(sqlText);
        } catch (JSQLParserException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 구문을 분석할 수 없습니다. 문법 또는 지원 여부를 확인하세요.", exception);
        }

        List<Statement> statements = parsed.getStatements();

        // 여러 문장을 한 항목에 넣어 실행하는 것을 방지한다.
        if (statements.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 항목에는 문장 하나만 등록할 수 있습니다.");
        }

        Statement statement = statements.get(0);

        if (!(statement instanceof Select select)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회용 SELECT 문만 허용합니다.");
        }

        return select;
    }
}