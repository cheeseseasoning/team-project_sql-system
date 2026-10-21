package com.sqlflow.execution.service;

import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleObjectName;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

/**
 * 단일 테이블의 SELECT 목록을 Oracle 메타데이터와 대조한다.
 * WHERE 등 다른 절, 사용자 권한, 실행 안전성은 별도 검사가 필요하다.
 */
@Service
@RequiredArgsConstructor
public class SqlSelectListValidationService {

    private final SqlParserValidator sqlParserValidator;
    private final SqlSelectColumnExtractor sqlSelectColumnExtractor;
    private final OracleTableValidationService oracleTableValidationService;
    private final OracleColumnValidationService oracleColumnValidationService;

    /**
     * SELECT 목록의 컬럼을 검사한다.
     * 정상 종료는 이 검사 범위의 통과를 의미하며, SQL 실행 허용을 의미하지 않는다.
     */
    @Transactional(readOnly = true)
    public void validateSelectList(String sqlText) {

        // 1. SQL 문장을 분석하고 단일 SELECT인지 확인한다.
        Select select = sqlParserValidator.parseSelect(sqlText);

        // 2. DB 조회 전에 지원 구조와 컬럼 접두어를 검사한다.
        Set<String> columnNames = sqlSelectColumnExtractor.extractColumns(select);

        // 추출기가 정상 종료했다면 PlainSelect와 단일 FROM 테이블임이 확인된다.
        PlainSelect plainSelect = (PlainSelect) select;
        Table fromTable = (Table) plainSelect.getFromItem();

        // 3. SQL에 적힌 이름을 실제 Oracle 객체로 연결한다.
        Map<String, OracleObjectName> resolvedObjects =
                oracleTableValidationService.validateTables(select);

        OracleObjectName target =
                resolvedObjects.get(fromTable.getFullyQualifiedName());

        if (target == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FROM 대상의 실제 Oracle 객체를 확인할 수 없습니다.");
        }

        // 4. 동의어라면 원래 이름이 아닌 실제 대상의 컬럼 목록과 비교한다.
        oracleColumnValidationService.validateColumns(target, columnNames);
    }
}