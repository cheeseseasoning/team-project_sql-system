package com.sqlflow.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.dto.ViewResourceInfoDTO;
import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.ai.domain.AiSchemaObject;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

import lombok.RequiredArgsConstructor;

/** Builds schema-only context from resources the signed-in user may query. */
@Service
@RequiredArgsConstructor
public class AiSchemaContextService {

    private final MySqlMetadataMapper metadataMapper;
    private final ViewResourceMapper resourceMapper;
    private final DataAccessValidationService accessValidationService;

    public List<AiSchemaObject> loadForUser(Long userId, Long deptId) {
        if (userId == null || deptId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 사용자 정보를 확인할 수 없습니다.");
        }

        String databaseName;
        try {
            databaseName = metadataMapper.selectCurrentDatabase();
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI SQL 생성에 필요한 DB 메타데이터에 연결할 수 없습니다.",
                    exception);
        }

        if (databaseName == null || databaseName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "현재 업무 데이터베이스를 확인할 수 없습니다.");
        }

        List<ViewResourceInfoDTO> allowedResources;
        try {
            allowedResources = resourceMapper.selectResourcesByDepartment(databaseName, deptId);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "부서별 조회 정책을 확인할 수 없습니다.",
                    exception);
        }

        List<AiSchemaObject> result = new ArrayList<>();
        for (ViewResourceInfoDTO resource : allowedResources) {
            QueryObjectName target = new QueryObjectName(resource.getSchemaName(), resource.getObjectName());

            // Reuse the same live policy and sensitive-role checks used by SELECT validation.
            try {
                accessValidationService.validate(userId, Set.of(target));
            } catch (ResponseStatusException denied) {
                if (denied.getStatusCode() == HttpStatus.FORBIDDEN) {
                    continue;
                }
                throw denied;
            }

            var object = metadataMapper.selectObject(databaseName, resource.getObjectName());
            if (object == null) {
                continue;
            }

            var columns = metadataMapper.selectColumns(databaseName, object.getObjectName()).stream()
                    .map(column -> new AiSchemaObject.Column(column.getColumnName(), column.getDataType()))
                    .toList();

            result.add(new AiSchemaObject(
                    object.getDatabaseName(),
                    object.getObjectName(),
                    object.getObjectType(),
                    resource.getResourceName(),
                    resource.getDescription(),
                    object.getObjectComment(),
                    columns));
        }

        return List.copyOf(result);
    }
}
