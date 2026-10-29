package com.sqlflow.request.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.create.view.CreateView;

/**
 * 신규 CREATE VIEW SQL의 기본 형태와 파싱 가능 여부를 검사한다.
 * 실제 DB 실행이나 참조 객체·권한 검사는 수행하지 않는다.
 */
@Service
public class ViewCreateSqlValidationService {

    private static final Pattern CREATE_VIEW_START = Pattern.compile("^CREATE\\s+VIEW\\b", Pattern.CASE_INSENSITIVE);

    public ViewCreateSqlValidationResultDTO validate(String sqlText) {

        if (sqlText == null || sqlText.isBlank()) {
            return result(Status.BLOCKED, "뷰 생성 SQL을 입력하세요.");
        }

        String sql = sqlText.strip();

        // 이번 범위는 신규 CREATE VIEW만 지원한다.
        // OR REPLACE, ALTER 및 다른 명령은 허용하지 않는다.
        if (!CREATE_VIEW_START.matcher(sql).find()) {
            return result(Status.BLOCKED, "신규 CREATE VIEW 문만 지원합니다.");
        }

        // 기존 입력 제한을 유지한다.
        // 마지막 세미콜론 하나는 허용하고, 내부 세미콜론은 차단한다.
        String body = sql.endsWith(";")
                ? sql.substring(0, sql.length() - 1)
                : sql;

        if (body.contains(";")) {
            return result(Status.BLOCKED, "SQL 항목 내부의 세미콜론은 지원하지 않습니다. " + "문장별로 나누어 입력하세요.");
        }

        List<Statement> statements;

        try {
            statements = CCJSqlParserUtil
                    .parseStatements(sql)
                    .getStatements();

        } catch (JSQLParserException exception) {
            // 파싱 실패만으로 문법 오류와 미지원 구문을 구분할 수 없다.
            return result(Status.WARNING, "SQL을 분석하지 못했습니다. 문법 오류 또는 " + "미지원 구문일 수 있으므로 DB팀 검토가 필요합니다.");
        }

        if (statements.size() != 1) {
            return result(Status.BLOCKED, "SQL 항목에는 CREATE VIEW 문 하나만 입력하세요.");
        }

        if (!(statements.get(0) instanceof CreateView createView)) {
            return result(Status.BLOCKED, "신규 CREATE VIEW 문만 지원합니다.");
        }

        if (createView.isOrReplace()) {
            return result(Status.BLOCKED, "기존 뷰를 변경하는 OR REPLACE는 지원하지 않습니다.");
        }

        return result(Status.VALID, "CREATE VIEW 기본 파싱을 통과했습니다. " + "실제 실행 가능 여부는 DB팀 확인이 필요합니다.");
    }

    private ViewCreateSqlValidationResultDTO result(Status status, String message) {

        return new ViewCreateSqlValidationResultDTO(status, message);
    }
}