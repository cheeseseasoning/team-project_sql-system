package com.sqlflow.request.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.execution.service.SqlExplainValidationService;
import com.sqlflow.mysql.service.MySqlTableValidationService;
import com.sqlflow.access.service.DataAccessValidationService;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;

@Service
@RequiredArgsConstructor
public class DmlSqlValidationService {
    private final SqlBasicValidator basicValidator;
    private final SqlExplainValidationService explainValidator;
    private final MySqlTableValidationService tableValidator;
    private final DataAccessValidationService accessValidator;

    /** 초안 저장은 DB 연결 없이 기본 형식만 검사한다. */
    public ValidationResult validate(SqlType sqlType, String sqlText) {
        if (sqlType == null) return new ValidationResult(Status.BLOCKED, "변경 유형을 선택하세요.");
        var basic = basicValidator.validate(sqlType.name(), sqlText);
        return new ValidationResult(basic.blocked() ? Status.BLOCKED : Status.VALID,
                basic.warning() == null ? "기본 형식을 확인했습니다. 제출 시 권한·EXPLAIN 검증을 다시 수행합니다." : basic.warning());
    }

    /** 변경 요청은 권한·객체 확인 실패를 경고로 전달하고 명확한 문법 오류만 차단한다. */
    public ValidationResult validateForReview(Long requesterId, SqlType type, String sql) {
        var basic = validate(type, sql);
        if (basic.status() == Status.BLOCKED) return basic;
        List<String> warnings = new ArrayList<>();
        var explain = explainValidator.validate(type.name(), sql);
        if (explain.state() == SqlExplainValidationService.State.SYNTAX_ERROR) {
            return new ValidationResult(Status.BLOCKED, explain.message());
        }
        if (explain.state() != SqlExplainValidationService.State.VALID) warnings.add(explain.message());
        try {
            var statement = CCJSqlParserUtil.parse(sql);
            var targets = tableValidator.validateStatementTables(statement);
            if (targets.isEmpty()) warnings.add("참조 객체를 확정하지 못해 접근 권한 검사를 완료하지 못했습니다.");
            else accessValidator.validate(requesterId, java.util.Set.copyOf(targets.values()));
            if ((type == SqlType.UPDATE && statement instanceof net.sf.jsqlparser.statement.update.Update update && update.getWhere() == null)
                    || (type == SqlType.DELETE && statement instanceof net.sf.jsqlparser.statement.delete.Delete delete && delete.getWhere() == null)) {
                warnings.add("WHERE 조건이 없습니다. 전체 행 변경 여부를 DB팀에서 검토해야 합니다.");
            }
        } catch (net.sf.jsqlparser.JSQLParserException | UnsupportedOperationException exception) {
            warnings.add("참조 객체를 분석하지 못했습니다. DB팀에서 대상과 접근 권한을 확인해야 합니다.");
        } catch (ResponseStatusException exception) {
            warnings.add("접근 권한 검토 필요: " + exception.getReason());
        } catch (DataAccessException | TransactionException exception) {
            warnings.add("접근 권한 검사를 완료하지 못했습니다. DB팀 확인이 필요합니다.");
        }
        return warnings.isEmpty() ? new ValidationResult(Status.VALID, "권한·EXPLAIN 검증을 통과했습니다. 실제 변경은 DB팀 검토 후 수행합니다.")
                : new ValidationResult(Status.WARNING, String.join(" / ", warnings));
    }

    public enum SqlType { INSERT, UPDATE, DELETE }
    public record ValidationResult(Status status, String message) { }
    public enum Status { VALID, WARNING, BLOCKED }
}
