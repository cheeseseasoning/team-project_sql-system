package com.sqlflow.request.service;

import java.util.List;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.mysql.mapper.MySqlViewMapper;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;
import com.sqlflow.request.service.CreateSqlTargetResolver.Kind;
import com.sqlflow.request.service.CreateSqlTargetResolver.Target;

/** 결과·이력 저장과 뷰 연결 변경을 같은 관리 DB 트랜잭션으로 처리한다. */
@Service
public class ViewCreationLinkService {
    private static final String NON_VIEW_CONFIRMATION = "[생성 대상 확인: 뷰 이외 객체] ";
    private final DepartmentRequestService results;
    private final MySqlViewMapper views;
    private final MySqlMetadataMapper metadata;
    private final JdbcTemplate jdbc;

    public ViewCreationLinkService(DepartmentRequestService results, MySqlViewMapper views,
            MySqlMetadataMapper metadata, @Qualifier("managementDataSource") DataSource dataSource) {
        this.results = results;
        this.views = views;
        this.metadata = metadata;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Transactional("managementTransactionManager")
    public void register(Long requestId, Long queryId, Long actorId, String status,
            String note, Long executedBy, Long resourceId, String viewLinkDecision) {
        // 기존 서비스의 DB팀·승인 상태·실행자 검사를 그대로 사용한다.
        results.registerChangeSqlResult(requestId, queryId, actorId, status, note, executedBy);
        synchronize(requestId, queryId, status, note, resourceId, viewLinkDecision, false);
    }

    @Transactional("managementTransactionManager")
    public void change(Long requestId, Long queryId, Long actorId, String status, String note,
            Long executedBy, String changeType, String changeReason, Long resourceId, String viewLinkDecision) {
        results.changeSqlResult(requestId, queryId, actorId, status, note, executedBy, changeType, changeReason);
        synchronize(requestId, queryId, status, note, resourceId, viewLinkDecision, true);
    }

    private void synchronize(Long requestId, Long queryId, String status, String note,
            Long resourceId, String decision, boolean canRelease) {
        // 같은 요청 안의 여러 SQL 결과가 동시에 바뀌어 연결 근거를 놓치지 않도록 직렬화한다.
        String type = jdbc.queryForObject(
                "SELECT REQUEST_TYPE FROM QUERY_REQUEST WHERE REQUEST_ID = ? FOR UPDATE", String.class, requestId);
        if (!"CREATE".equals(type)) {
            rejectUnexpectedResource(resourceId);
            return;
        }
        String sql = jdbc.queryForObject(
                "SELECT SQL_CONTENT FROM DB_CHANGE_SQL WHERE REQUEST_ID = ? AND QUERY_ID = ?",
                String.class, requestId, queryId);
        Target target = CreateSqlTargetResolver.resolve(sql);
        if (target.kind() == Kind.OTHER) {
            rejectUnexpectedResource(resourceId);
            return;
        }

        boolean completed = "COMPLETED".equals(status);
        boolean manualView = target.kind() == Kind.UNKNOWN && "VIEW".equals(decision);
        if (target.kind() == Kind.UNKNOWN && completed) {
            if ((!manualView && !"OTHER".equals(decision)) || note == null || note.isBlank()) {
                throw badRequest("생성 대상을 분석하지 못했습니다. DB팀이 뷰 생성 여부를 선택하고 처리 메모에 확인 내용을 입력해 주세요.");
            }
            if ("OTHER".equals(decision)) {
                rejectUnexpectedResource(resourceId);
                // 과거 연결의 SQL 항목을 특정할 수 없으므로 임의로 남기거나 해제하지 않는다.
                if (canRelease) requireNoUnidentifiedLinks(requestId, queryId);
                recordManualDecision(queryId, note, decision);
                return;
            }
        }
        if (target.kind() == Kind.UNKNOWN && (manualView || "OTHER".equals(decision))) {
            recordManualDecision(queryId, note, decision);
        }
        if (completed) {
            if (resourceId == null) throw badRequest("뷰 생성 성공 결과에는 생성된 뷰를 선택해 주세요.");
            Resource resource = loadResource(resourceId);
            if (!manualView) validateTarget(target, resource);
            else if (canRelease) {
                // 분석 불가 항목의 연결 대상을 바꿔 기존 권한 근거를 남겨두지 않도록 한다.
                for (Resource previous : linkedResources(requestId)) {
                    if (!previous.id().equals(resourceId) && !hasOtherSuccessfulSource(requestId, queryId, previous)) {
                        throw conflict("이 SQL의 기존 연결 대상을 확정할 수 없어 다른 뷰로 자동 변경할 수 없습니다. DB팀 확인이 필요합니다.");
                    }
                }
            }
            if (views.countView(resource.schema(), resource.name()) != 1) {
                throw badRequest("업무 DB에 존재하는 뷰를 선택해 주세요.");
            }
            int updated = jdbc.update("""
                    UPDATE DATA_RESOURCE SET CREATE_REQUEST_ID = ?
                    WHERE RESOURCE_ID = ? AND SCHEMA_NAME = ? AND OBJECT_NAME = ?
                      AND (CREATE_REQUEST_ID IS NULL OR CREATE_REQUEST_ID = ?)
                    """, requestId, resourceId, resource.schema(), resource.name(), requestId);
            if (updated != 1) throw conflict("다른 생성 요청에 연결됐거나 변경된 뷰입니다. 기존 연결은 자동으로 이전하지 않습니다.");
            return;
        }

        if (!canRelease) {
            rejectUnexpectedResource(resourceId);
            return;
        }
        List<Resource> linked = linkedResources(requestId);
        if (linked.isEmpty()) {
            rejectUnexpectedResource(resourceId);
            return;
        }
        Resource release;
        if (target.kind() == Kind.UNKNOWN) {
            // 모든 연결을 다른 성공한 뷰 생성 항목이 뒷받침하면 현재 항목이 해제할 연결은 없다.
            // 관계가 불명확한 연결이 하나라도 있으면 기존의 수동 확인 절차를 유지한다.
            if (!manualView && resourceId == null
                    && linked.stream().allMatch(resource -> hasOtherSuccessfulSource(requestId, queryId, resource))) return;
            if (!manualView || resourceId == null || note == null || note.isBlank()) {
                throw conflict("기존 뷰 연결의 생성 대상을 확정하지 못했습니다. DB팀이 연결 해제 대상 뷰와 확인 메모를 지정해 주세요.");
            }
            release = linked.stream().filter(resource -> resource.id().equals(resourceId)).findFirst()
                    .orElseThrow(() -> badRequest("이 요청에 연결된 뷰를 선택해 주세요."));
        } else {
            rejectUnexpectedResource(resourceId);
            String schema = schema(target);
            release = linked.stream().filter(resource -> sameName(schema, resource.schema())
                    && sameName(target.view(), resource.name())).findFirst().orElse(null);
            if (release == null) return;
        }
        if (hasOtherSuccessfulSource(requestId, queryId, release)) return;
        // 논리명·설명은 보존하고, 요청 부서 권한의 근거가 되는 연결만 해제한다.
        jdbc.update("UPDATE DATA_RESOURCE SET CREATE_REQUEST_ID = NULL WHERE RESOURCE_ID = ? AND CREATE_REQUEST_ID = ?",
                release.id(), requestId);
    }

    /** 수동 확인은 기존 처리 메모에 남겨 별도 테이블 없이 정정 이력에도 보존한다. */
    private void recordManualDecision(Long queryId, String note, String decision) {
        if (note == null || note.isBlank()) throw badRequest("생성 대상 확인 내용을 처리 메모에 입력해 주세요.");
        String text = note.strip().replaceFirst("^\\[생성 대상 확인: (뷰|뷰 이외 객체)\\]\\s*", "");
        String confirmed = ("VIEW".equals(decision) ? "[생성 대상 확인: 뷰] " : NON_VIEW_CONFIRMATION) + text;
        if (confirmed.length() > 1000) throw badRequest("생성 대상 확인 문구를 포함해 처리 메모를 1,000자 이내로 입력해 주세요.");
        if (jdbc.update("UPDATE DB_CHANGE_SQL_RESULT SET RESULT_NOTE = ? WHERE QUERY_ID = ?", confirmed, queryId) != 1) {
            throw conflict("생성 대상 확인 내용을 저장하지 못했습니다.");
        }
    }

    private boolean hasOtherSuccessfulSource(Long requestId, Long queryId, Resource resource) {
        List<SuccessfulSql> sqls = jdbc.query("""
                SELECT V.SQL_CONTENT, CR.RESULT_NOTE FROM DB_CHANGE_SQL V
                JOIN DB_CHANGE_SQL_RESULT CR ON CR.QUERY_ID = V.QUERY_ID
                WHERE V.REQUEST_ID = ? AND V.QUERY_ID <> ? AND CR.RESULT_STATUS = 'COMPLETED'
                """, (rs, row) -> new SuccessfulSql(rs.getString(1), rs.getString(2)), requestId, queryId);
        boolean unknown = false;
        for (SuccessfulSql sql : sqls) {
            Target target = CreateSqlTargetResolver.resolve(sql.text());
            if (target.kind() == Kind.UNKNOWN) {
                // 성공 등록 시 저장한 정해진 비뷰 확인 표시만 사용한다. 일반 설명은 확인으로 간주하지 않는다.
                // 분석 가능한 CREATE VIEW는 메모 내용과 관계없이 아래의 실제 생성 대상으로 판단한다.
                boolean confirmedNonView = sql.note() != null && sql.note().startsWith(NON_VIEW_CONFIRMATION)
                        && !sql.note().substring(NON_VIEW_CONFIRMATION.length()).isBlank();
                if (!confirmedNonView) unknown = true;
            }
            if (target.kind() == Kind.VIEW && sameName(schema(target), resource.schema())
                    && sameName(target.view(), resource.name())) return true;
        }
        if (unknown) throw conflict("다른 성공 SQL의 생성 대상을 확정하지 못해 연결을 자동 해제할 수 없습니다. DB팀 확인이 필요합니다.");
        return false;
    }

    private void validateTarget(Target target, Resource resource) {
        var actual = metadata.selectObject(schema(target), target.view());
        if (actual == null || !"VIEW".equalsIgnoreCase(actual.getObjectType())
                || !actual.getDatabaseName().equals(resource.schema()) || !actual.getObjectName().equals(resource.name())) {
            throw badRequest("선택한 뷰가 SQL의 생성 대상과 일치하지 않습니다. 스키마명과 뷰 이름을 확인해 주세요.");
        }
    }

    private String schema(Target target) {
        String schema = target.schema() == null ? metadata.selectCurrentDatabase() : target.schema();
        if (schema == null || schema.isBlank()) throw conflict("생성 대상의 기본 스키마를 확인할 수 없습니다. DB팀 확인이 필요합니다.");
        return schema;
    }

    private boolean sameName(String first, String second) {
        return first.equals(second) || (metadata.selectLowerCaseTableNames() != 0 && first.equalsIgnoreCase(second));
    }

    private Resource loadResource(Long resourceId) {
        List<Resource> resources = jdbc.query("""
                SELECT RESOURCE_ID, SCHEMA_NAME, OBJECT_NAME FROM DATA_RESOURCE WHERE RESOURCE_ID = ?
                """, (rs, row) -> new Resource(rs.getLong(1), rs.getString(2), rs.getString(3)), resourceId);
        if (resources.size() != 1) throw badRequest("등록된 뷰 리소스를 선택해 주세요.");
        return resources.getFirst();
    }

    private List<Resource> linkedResources(Long requestId) {
        return jdbc.query("""
                SELECT RESOURCE_ID, SCHEMA_NAME, OBJECT_NAME FROM DATA_RESOURCE WHERE CREATE_REQUEST_ID = ?
                """, (rs, row) -> new Resource(rs.getLong(1), rs.getString(2), rs.getString(3)), requestId);
    }

    private void requireNoUnidentifiedLinks(Long requestId, Long queryId) {
        for (Resource resource : linkedResources(requestId)) {
            if (!hasOtherSuccessfulSource(requestId, queryId, resource)) {
                throw conflict("생성 대상을 분석하지 못한 SQL과 기존 뷰 연결의 관계를 DB팀에서 확인해야 합니다.");
            }
        }
    }

    private void rejectUnexpectedResource(Long resourceId) {
        if (resourceId != null) throw badRequest("뷰 생성 성공 또는 생성 대상 확인에 필요한 경우에만 뷰를 지정할 수 있습니다.");
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Resource(Long id, String schema, String name) { }
    private record SuccessfulSql(String text, String note) { }
}
