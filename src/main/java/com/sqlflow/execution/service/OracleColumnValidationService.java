package com.sqlflow.execution.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.domain.OracleObjectName;
import com.sqlflow.execution.mapper.OracleMetadataMapper;

import lombok.RequiredArgsConstructor;

/**
 * 실제 Oracle 객체의 컬럼 목록과 SQL에서 참조한 컬럼 이름을 비교한다.
 * 테이블 별칭 해석과 사용자 접근 권한 검사는 별도로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class OracleColumnValidationService {

    private final OracleMetadataMapper oracleMetadataMapper;

    /**
     * target은 동의어 해석까지 완료한 실제 Oracle 객체다.
     * columnNames에는 테이블·별칭 접두어를 제외한 컬럼 이름을 전달한다.
     * '*'는 개별 컬럼 이름이 아니므로 호출하는 쪽에서 별도로 처리한다.
     */
    @Transactional(readOnly = true)
    public void validateColumns(OracleObjectName target, Set<String> columnNames) {

        if (target == null || columnNames == null) {
            throw new IllegalArgumentException("검사 대상 객체와 컬럼 목록이 필요합니다.");
        }

        // 같은 테이블의 컬럼마다 DB를 조회하지 않고 목록을 한 번에 가져온다.
        List<OracleColumnMetadata> columns = oracleMetadataMapper.selectColumns(target.owner(), target.objectName());

        if (columns.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 대상의 컬럼 정보를 확인할 수 없습니다.");
        }

        Set<String> existingNames = new HashSet<>();

        for (OracleColumnMetadata column : columns) {
            // Oracle 데이터 사전에서 받은 실제 이름은 대소문자를 변경하지 않는다.
            existingNames.add(column.getColumnName());
        }

        for (String columnName : columnNames) {
            String normalizedName = normalizeColumnName(columnName);

            if (!existingNames.contains(normalizedName)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "조회 대상에 존재하지 않는 컬럼입니다: " + columnName);
            }
        }
    }

    /** 일반 이름은 대문자로 변환하고, 큰따옴표로 감싼 이름은 대소문자를 유지한다. */
    private String normalizeColumnName(String columnName) {

        if (columnName == null || columnName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "검사할 컬럼 이름이 없습니다.");
        }

        if (columnName.length() >= 2 && columnName.startsWith("\"") && columnName.endsWith("\"")) {
            return columnName.substring(1, columnName.length() - 1).replace("\"\"", "\"");
        }

        return columnName.toUpperCase(Locale.ROOT);
    }
}