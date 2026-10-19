package com.sqlflow.execution.service;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;

/**
 * 파싱된 SELECT에서 참조 테이블·뷰 이름을 추출한다.
 * 실제 객체의 존재 여부와 접근 권한은 별도 검사한다.
 */
@Component
public class SqlTableExtractor {

    /** SQL 문장 전체를 분석해 참조 객체 이름을 중복 없이 반환한다. */
    public Set<String> extractTables(Select select) {

        if (select == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "분석된 SELECT 정보가 없습니다.");
        }

        // 분석 상태가 다른 요청과 섞이지 않도록 호출마다 새 객체를 만든다.
        TablesNamesFinder<Void> finder = new TablesNamesFinder<>();

        try {
            return Set.copyOf(finder.getTables((Statement) select));
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "참조 테이블을 분석할 수 없는 SQL 구조입니다.", exception);
        }
    }
}