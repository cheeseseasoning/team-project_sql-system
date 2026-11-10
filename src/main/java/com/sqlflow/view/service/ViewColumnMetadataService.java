package com.sqlflow.view.service;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.mysql.dto.MySqlViewColumnDTO;

@Service
public class ViewColumnMetadataService {

    private final ViewQueryService views;
    private final ViewResourceMapper resources;
    private final JdbcTemplate jdbc;

    public ViewColumnMetadataService(ViewQueryService views, ViewResourceMapper resources,
            @Qualifier("managementDataSource") DataSource dataSource) {
        this.views = views;
        this.resources = resources;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public MetadataResponse read(String schema, String view, Long userId) {
        views.getViewDetail(schema, view, userId);
        return new MetadataResponse(canEdit(schema, view, userId), jdbc.query("""
                SELECT COLUMN_NAME, LOGICAL_NAME, DESCRIPTION
                FROM VIEW_COLUMN_METADATA WHERE SCHEMA_NAME = ? AND OBJECT_NAME = ?
                """, (rs, row) -> new ColumnMetadata(rs.getString(1), rs.getString(2), rs.getString(3)), schema, view));
    }

    @Transactional("managementTransactionManager")
    public void save(String schema, String view, Long userId, List<ColumnMetadata> entries) {
        var detail = views.getViewDetail(schema, view, userId);
        if (!canEdit(schema, view, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "컬럼 정보를 편집할 권한이 없습니다.");
        }

        var actualColumns = detail.columns().stream().map(MySqlViewColumnDTO::getColumnName).collect(Collectors.toSet());
        var submitted = new HashSet<String>();
        if (entries == null || entries.size() > actualColumns.size()) {
            throw invalidColumn();
        }
        for (ColumnMetadata entry : entries) {
            if (entry == null || !actualColumns.contains(entry.columnName()) || !submitted.add(entry.columnName())) {
                throw invalidColumn();
            }
            String name = trim(entry.logicalName());
            String description = trim(entry.description());
            if ((name != null && name.length() > 255) || (description != null && description.length() > 1000)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "논리명은 255자, 설명은 1,000자까지 입력할 수 있습니다.");
            }
            jdbc.update("""
                    MERGE INTO VIEW_COLUMN_METADATA M
                    USING (SELECT ? SCHEMA_NAME, ? OBJECT_NAME, ? COLUMN_NAME FROM DUAL) S
                    ON (M.SCHEMA_NAME = S.SCHEMA_NAME AND M.OBJECT_NAME = S.OBJECT_NAME AND M.COLUMN_NAME = S.COLUMN_NAME)
                    WHEN MATCHED THEN UPDATE SET M.LOGICAL_NAME = ?, M.DESCRIPTION = ?, M.UPDATED_AT = SYSTIMESTAMP
                    WHEN NOT MATCHED THEN INSERT (SCHEMA_NAME, OBJECT_NAME, COLUMN_NAME, LOGICAL_NAME, DESCRIPTION)
                        VALUES (S.SCHEMA_NAME, S.OBJECT_NAME, S.COLUMN_NAME, ?, ?)
                    """, schema, view, entry.columnName(), name, description, name, description);
        }
    }

    private boolean canEdit(String schema, String view, Long userId) {
        if (resources.selectActiveDbTeamMember(userId) == 1) {
            return true;
        }

        // 현재 활성 소속을 생성 기안 당시의 부서와 비교한다.
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM USERS U
                WHERE U.USER_ID = ? AND U.ENABLED = 'Y'
                  AND EXISTS (
                      SELECT 1 FROM DATA_RESOURCE R
                      JOIN QUERY_REQUEST Q ON Q.REQUEST_ID = R.CREATE_REQUEST_ID
                      WHERE R.SCHEMA_NAME = ? AND R.OBJECT_NAME = ?
                        AND Q.DEPT_ID = U.DEPT_ID AND Q.REQUEST_TYPE = 'CREATE_VIEW'
                  )
                """, Integer.class, userId, schema, view);
        return count != null && count > 0;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ResponseStatusException invalidColumn() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "현재 뷰의 컬럼 목록을 다시 확인해 주세요.");
    }

    public record ColumnMetadata(String columnName, String logicalName, String description) { }

    public record MetadataResponse(boolean canEdit, List<ColumnMetadata> columns) { }
}
