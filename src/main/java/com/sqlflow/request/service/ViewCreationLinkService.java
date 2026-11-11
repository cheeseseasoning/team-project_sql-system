package com.sqlflow.request.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.mysql.mapper.MySqlViewMapper;

@Service
public class ViewCreationLinkService {
    private final DepartmentRequestService results;
    private final MySqlViewMapper views;
    private final JdbcTemplate jdbc;

    public ViewCreationLinkService(DepartmentRequestService results, MySqlViewMapper views,
            @Qualifier("managementDataSource") DataSource dataSource) {
        this.results = results;
        this.views = views;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional("managementTransactionManager")
    public void register(Long requestId, Long queryId, Long actorId, String status,
            String note, Long executedBy, Long resourceId) {
        results.registerChangeSqlResult(requestId, queryId, actorId, status, note, executedBy);
        String requestType = jdbc.queryForObject(
                "SELECT REQUEST_TYPE FROM QUERY_REQUEST WHERE REQUEST_ID = ?", String.class, requestId);
        boolean linkView = "CREATE_VIEW".equals(requestType) && "COMPLETED".equals(status);
        if (!linkView && resourceId == null) {
            return;
        }
        if (!linkView || resourceId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "뷰 생성 성공 결과에는 생성된 뷰를 선택해 주세요.");
        }

        var resources = jdbc.query("""
                SELECT SCHEMA_NAME, OBJECT_NAME FROM DATA_RESOURCE WHERE RESOURCE_ID = ?
                """, (rs, row) -> new ViewName(rs.getString(1), rs.getString(2)), resourceId);
        if (resources.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록된 뷰 리소스를 선택해 주세요.");
        }
        ViewName view = resources.getFirst();
        if (views.countView(view.schema(), view.name()) != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "업무 DB에 존재하는 뷰를 선택해 주세요.");
        }

        // 다른 기안서와 이미 연결된 리소스를 동시에 덮어쓰지 못하게 한다.
        int updated = jdbc.update("""
                UPDATE DATA_RESOURCE SET CREATE_REQUEST_ID = ?
                WHERE RESOURCE_ID = ? AND SCHEMA_NAME = ? AND OBJECT_NAME = ?
                  AND (CREATE_REQUEST_ID IS NULL OR CREATE_REQUEST_ID = ?)
                """, requestId, resourceId, view.schema(), view.name(), requestId);
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "뷰의 리소스가 변경됐거나 다른 생성 기안서에 연결돼 있습니다.");
        }
    }

    private record ViewName(String schema, String name) { }
}
