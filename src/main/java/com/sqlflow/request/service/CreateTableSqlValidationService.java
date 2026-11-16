package com.sqlflow.request.service;

import java.util.List;
import java.util.regex.Pattern;

import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.table.CreateTable;

/** Parser-only validation for one new CREATE TABLE draft. */
public final class CreateTableSqlValidationService {

    private static final Pattern CREATE_TABLE_START =
            Pattern.compile("^CREATE\\s+TABLE\\b", Pattern.CASE_INSENSITIVE);

    private CreateTableSqlValidationService() {
    }

    public static ViewCreateSqlValidationResultDTO validate(String sqlText) {
        if (sqlText == null || sqlText.isBlank()) {
            return result(Status.BLOCKED, "테이블 생성 SQL을 입력하세요.");
        }

        String sql = sqlText.strip();
        if (!CREATE_TABLE_START.matcher(sql).find()) {
            return result(Status.BLOCKED, "CREATE TABLE 문만 입력하세요.");
        }

        String body = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
        if (body.contains(";")) {
            return result(Status.BLOCKED, "SQL 항목에는 CREATE TABLE 문 하나만 입력하세요.");
        }

        try {
            List<Statement> statements = CCJSqlParserUtil.parseStatements(sql).getStatements();
            if (statements.size() != 1 || !(statements.get(0) instanceof CreateTable createTable)) {
                return result(Status.BLOCKED, "SQL 항목에는 CREATE TABLE 문 하나만 입력하세요.");
            }
            if (createTable.getTable() == null || createTable.getTable().getName() == null
                    || createTable.getTable().getName().isBlank()) {
                return result(Status.BLOCKED, "생성할 테이블 이름을 지정하세요.");
            }
            if (createTable.getTable().getSchemaName() != null
                    && !createTable.getTable().getSchemaName().isBlank()) {
                return result(Status.BLOCKED, "현재 연결된 데이터베이스 안에 테이블을 생성하세요.");
            }
            return result(Status.VALID, "CREATE TABLE 구문을 확인했습니다. 실제 생성과 기존 이름 충돌은 DB팀이 확인합니다.");
        } catch (JSQLParserException exception) {
            return result(Status.WARNING, "CREATE TABLE을 분석하지 못했습니다. DB팀 검토가 필요합니다.");
        }
    }

    private static ViewCreateSqlValidationResultDTO result(Status status, String message) {
        return new ViewCreateSqlValidationResultDTO(status, message);
    }
}
