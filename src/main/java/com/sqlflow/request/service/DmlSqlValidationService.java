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

    private static final Pattern DML_START =
            Pattern.compile("^(INSERT|UPDATE|DELETE)\\b", Pattern.CASE_INSENSITIVE);

    /** 선택한 유형과 SQL 한 항목의 문장 종류가 일치하는지 확인한다. */
    public ValidationResult validate(SqlType sqlType, String sqlText) {

        if (sqlType == null) {
            return new ValidationResult(Status.BLOCKED, "변경 유형을 선택하세요.");
        }

        if (sqlText == null || sqlText.isBlank()) {
            return new ValidationResult(Status.BLOCKED, "변경 SQL을 입력하세요.");
        }

        String sql = sqlText.strip();
        var firstKeyword = DML_START.matcher(sql);

        // 첫 명령이 선택한 유형과 다르면 파싱 전에 차단한다.
        if (!firstKeyword.find() || !sqlType.name().equalsIgnoreCase(firstKeyword.group(1))) {
            return new ValidationResult(
                    Status.BLOCKED,
                    sqlType.name() + " 유형에는 " + sqlType.name() + " 문을 입력하세요.");
        }

        List<Statement> statements;

        try {
            statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
        } catch (JSQLParserException exception) {
            return new ValidationResult(
                    Status.WARNING,
                    "SQL을 분석하지 못했습니다. 문법 오류 또는 파서 미지원 구문인지 확인해 주세요.");
        }

        if (statements.size() != 1) {
            return new ValidationResult(Status.BLOCKED, "SQL 항목마다 한 문장만 입력하세요.");
        }

        if (!sqlType.statementClass.isInstance(statements.get(0))) {
            return new ValidationResult(Status.BLOCKED, "선택한 유형과 SQL 문장의 종류가 다릅니다.");
        }

        return new ValidationResult(Status.VALID, sqlType.name() + " 기본 구문 검사를 통과했습니다.");
    }

    public enum SqlType {
        INSERT(Insert.class),
        UPDATE(Update.class),
        DELETE(Delete.class);

        private final Class<? extends Statement> statementClass;

        SqlType(Class<? extends Statement> statementClass) {
            this.statementClass = statementClass;
        }
    }

    public record ValidationResult(Status status, String message) {
    }

    public enum Status {
        VALID,
        WARNING,
        BLOCKED
    }
}