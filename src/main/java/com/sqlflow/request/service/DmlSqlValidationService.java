package com.sqlflow.request.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;

@Service
public class DmlSqlValidationService {

    private static final Pattern DML_START = Pattern.compile("^(INSERT|UPDATE|DELETE)\\b", Pattern.CASE_INSENSITIVE);

    /** 변경 신청의 SQL 한 항목을 파싱하고 허용된 문장인지 확인한다. */
    public ValidationResult validate(String sqlText) {

        if (sqlText == null || sqlText.isBlank()) {
            return new ValidationResult(Status.BLOCKED, "변경 SQL을 입력하세요.");
        }

        String sql = sqlText.strip();

        // 지원 범위를 명확하게 제한한다. 주석이나 WITH로 시작하는 입력은 제외한다.
        if (!DML_START.matcher(sql).find()) {
            return new ValidationResult(Status.BLOCKED, "INSERT, UPDATE, DELETE로 시작하는 문장만 지원합니다.");
        }

        List<Statement> statements;

        try {
            statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
        } catch (JSQLParserException exception) {
            // 파싱 실패만으로 문법 오류와 파서 미지원 구문을 구분하기 어렵다.
            return new ValidationResult(Status.WARNING, "SQL을 분석하지 못했습니다. 문법 오류 또는 미지원 구문일 수 있으므로 DB팀 검토가 필요합니다.");
        }

        if (statements.size() != 1) {
            return new ValidationResult(Status.BLOCKED, "SQL 항목에는 한 문장만 입력하세요.");
        }

        Statement statement = statements.get(0);

        if (!(statement instanceof Insert || statement instanceof Update || statement instanceof Delete)) {
            return new ValidationResult(Status.BLOCKED, "INSERT, UPDATE, DELETE 문만 지원합니다.");
        }

        return new ValidationResult(Status.VALID, "변경 SQL 기본 구문 검사를 통과했습니다.");
    }

    public record ValidationResult(Status status, String message) {
    }

    public enum Status {
        VALID,
        WARNING,
        BLOCKED
    }
}