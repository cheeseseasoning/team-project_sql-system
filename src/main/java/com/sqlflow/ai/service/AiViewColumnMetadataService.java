package com.sqlflow.ai.service;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Oracle에 등록된 뷰 컬럼의 논리명과 설명을 AI 참고 정보로 조회한다. */
@Service
public class AiViewColumnMetadataService {

    private final JdbcTemplate jdbc;

    public AiViewColumnMetadataService(
            @Qualifier("managementDataSource") DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 물리 컬럼명을 키로 반환한다.
     * 등록 정보가 없으면 빈 Map을 반환하며, 실제 컬럼 존재 여부는 MySQL에서 확인한다.
     */
    @Transactional(transactionManager = "managementTransactionManager", readOnly = true)
    public Map<String, ColumnMeaning> load(String schemaName, String objectName) {
        var entries = jdbc.query("""
                SELECT COLUMN_NAME, LOGICAL_NAME, DESCRIPTION
                FROM VIEW_COLUMN_METADATA
                WHERE SCHEMA_NAME = ? AND OBJECT_NAME = ?
                ORDER BY COLUMN_NAME
                """,
                (rs, rowNum) -> new ColumnMeaning(
                        rs.getString("COLUMN_NAME"),
                        trimToNull(rs.getString("LOGICAL_NAME")),
                        trimToNull(rs.getString("DESCRIPTION"))),
                schemaName, objectName);

        Map<String, ColumnMeaning> result = new LinkedHashMap<>();
        for (ColumnMeaning entry : entries) {
            result.put(entry.columnName(), entry);
        }
        return Map.copyOf(result);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record ColumnMeaning(
            String columnName,
            String logicalName,
            String description) {
    }
}