package com.sqlflow.execution.service;

import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.service.MySqlTableValidationService;
import lombok.RequiredArgsConstructor;

/** 구문은 EXPLAIN에 맡기고 파서는 접근 정책에 필요한 참조 객체만 추출한다. */
@Service
@RequiredArgsConstructor
public class MySqlQueryMetadataValidationService {
    private final SqlParserValidator sqlParserValidator;
    private final MySqlTableValidationService tableValidationService;
    private final SqlBasicValidator basicValidator;
    private final SqlExplainValidationService explainValidator;

    public ValidationResult validate(String sqlText) {
        var basic = basicValidator.validate("SELECT", sqlText);
        if (basic.blocked()) throw badRequest(basic.warning());
        var explain = explainValidator.validate("SELECT", sqlText);
        if (explain.state() != SqlExplainValidationService.State.VALID) {
            throw new ResponseStatusException(explain.state() == SqlExplainValidationService.State.INCOMPLETE
                    ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_REQUEST, explain.message());
        }
        // JOIN·서브쿼리·CTE·UNION의 표현식/컬럼 지원 목록 검사를 적용하지 않는다.
        // 객체 추출을 확정할 수 없는 SELECT는 기존 권한 정책을 우회하지 못한다.
        net.sf.jsqlparser.statement.select.Select select;
        try {
            select = sqlParserValidator.parseSelect(sqlText);
        } catch (ResponseStatusException exception) {
            throw badRequest("EXPLAIN은 통과했지만 참조 객체를 분석하지 못해 접근 권한을 검사할 수 없습니다.");
        }
        Set<QueryObjectName> targets;
        try {
            targets = Set.copyOf(tableValidationService.validateTables(select).values());
        } catch (UnsupportedOperationException exception) {
            throw badRequest("참조 객체를 확정하지 못해 접근 권한을 검사할 수 없습니다.");
        }
        if (targets.isEmpty()) throw badRequest("참조 객체를 확정하지 못해 접근 권한을 검사할 수 없습니다.");
        return new ValidationResult(targets);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** 접근 권한 검사에 사용하는 실제 참조 객체만 반환한다. */
    public record ValidationResult(Set<QueryObjectName> resources) {
        public ValidationResult {
            resources = Set.copyOf(resources);
        }
    }
}
