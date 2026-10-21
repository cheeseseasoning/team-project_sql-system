package com.sqlflow.execution.service;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SelectItem;

/**
 * 단일 테이블 SELECT 목록에서 일반 컬럼 이름을 추출한다.
 * WHERE 등 다른 절의 컬럼과 실제 Oracle 존재 여부는 검사하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class SqlSelectColumnExtractor {

    private final SqlColumnQualifierValidator qualifierValidator;

    /**
     * 컬럼 접두어를 확인하고, Oracle 메타데이터와 비교할 이름을 반환한다.
     * 아직 지원하지 않는 SELECT 목록 표현식은 건너뛰지 않고 차단한다.
     */
    public Set<String> extractColumns(Select select) {

        if (select == null) {
            throw new IllegalArgumentException("분석된 SELECT 정보가 필요합니다.");
        }

        if (!(select instanceof PlainSelect plainSelect)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 컬럼 추출은 단일 SELECT 구조만 지원합니다.");
        }

        // WITH의 이름은 실제 테이블이 아닌 임시 조회 결과일 수 있다.
        if (plainSelect.getWithItemsList() != null
                && !plainSelect.getWithItemsList().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WITH 구문의 컬럼 추출은 아직 지원하지 않습니다.");
        }

        if (!(plainSelect.getFromItem() instanceof Table fromTable)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FROM에 테이블 하나를 지정한 조회만 지원합니다.");
        }

        // JOIN과 쉼표로 나열한 복수 테이블은 별도의 대상 구분이 필요하다.
        if (plainSelect.getJoins() != null
                && !plainSelect.getJoins().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "복수 테이블의 컬럼 추출은 아직 지원하지 않습니다.");
        }

        if (plainSelect.getSelectItems() == null
                || plainSelect.getSelectItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회할 SELECT 항목이 없습니다.");
        }

        Set<String> columnNames = new LinkedHashSet<>();

        for (SelectItem<?> item : plainSelect.getSelectItems()) {

            // 함수·계산식·상수·별표는 각각의 분석 규칙을 추가한 뒤 지원한다.
            if (!(item.getExpression() instanceof Column column)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 SELECT 목록은 일반 컬럼만 지원합니다. 함수·계산식·상수·별표는 아직 지원하지 않습니다.");
            }

            qualifierValidator.validateQualifier(column, fromTable);

            // 출력 별칭이 아닌 원본 컬럼 이름을 보관한다.
            columnNames.add(column.getColumnName());
        }

        return Set.copyOf(columnNames);
    }
}