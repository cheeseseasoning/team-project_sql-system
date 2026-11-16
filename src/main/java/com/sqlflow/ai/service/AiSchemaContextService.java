package com.sqlflow.ai.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** 실제 MySQL 구조에 업무용 논리명과 설명을 결합하며, 행 데이터는 조회하지 않는다. */
@Service
@RequiredArgsConstructor
public class AiSchemaContextService {

    private final MySqlMetadataMapper metadataMapper;
    private final ViewResourceMapper resourceMapper;
    private final DataAccessValidationService accessValidationService;
    private final AiViewColumnMetadataService viewColumnMetadataService;

    /**
     * AI 초안 작성에는 현재 업무 DB의 객체 구조를 제공한다.
     * 부서 접근 권한은 초안 작성과 분리하고 기존 요청 검증에서 확인한다.
     */
    public List<AiSchemaObject> loadForUser(Long userId, Long deptId) {
        requireUser(userId, deptId);
        String databaseName = currentDatabase();

        try {
            Map<String, ViewResourceInfoDTO> resources = new java.util.HashMap<>();
            for (var resource : resourceMapper.selectResources(databaseName)) {
                resources.put(resource.getObjectName(), resource);
            }

            List<AiSchemaObject> result = new ArrayList<>();
            for (var object : metadataMapper.selectSelectableObjects()) {
                if (!databaseName.equals(object.getDatabaseName())) continue;

                Map<String, AiViewColumnMetadataService.ColumnMeaning> meanings =
                        "VIEW".equals(object.getObjectType())
                                ? viewColumnMetadataService.load(object.getDatabaseName(), object.getObjectName())
                                : Map.of();

                var columns = loadColumns(object.getDatabaseName(), object.getObjectName(), meanings);
                var resource = resources.get(object.getObjectName());
                String[] comment = splitComment(object.getObjectComment());

                result.add(new AiSchemaObject(
                        object.getDatabaseName(),
                        object.getObjectName(),
                        object.getObjectType(),
                        resource == null ? comment[0] : resource.getResourceName(),
                        resource == null ? comment[1] : resource.getDescription(),
                        object.getObjectComment(),
                        columns));
            }

            return List.copyOf(result);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI SQL 생성에 필요한 객체 구조 또는 논리명을 조회하지 못했습니다. DB 연결 상태를 확인하세요.",
                    exception);
        }
    }

    /**
     * 자연어 요청에서 대상을 찾을 수 있도록 현재 업무 DB의 기본 테이블 구조를 제공한다.
     * 행 데이터는 조회하지 않으며, 부서 접근 권한은 기존 DML 검증 단계에서 확인한다.
     */
    public List<AiSchemaObject> loadForDml(Long userId, Long deptId) {
        requireUser(userId, deptId);
        String databaseName = currentDatabase();

        try {
            List<AiSchemaObject> result = new ArrayList<>();

            for (var object : metadataMapper.selectSelectableObjects()) {
                // 현재 업무 DB의 기본 테이블만 사용하고 뷰는 제외한다.
                if (!databaseName.equals(object.getDatabaseName())
                        || !"BASE TABLE".equals(object.getObjectType())) {
                    continue;
                }

                var columns = loadColumns(object.getDatabaseName(), object.getObjectName(), Map.of());
                if (columns.isEmpty()) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "DML AI 초안 작성에 필요한 컬럼 정보를 확인할 수 없습니다: "
                                    + object.getObjectName());
                }

                String[] meaning = splitComment(object.getObjectComment());

                result.add(new AiSchemaObject(
                        object.getDatabaseName(),
                        object.getObjectName(),
                        object.getObjectType(),
                        meaning[0],
                        meaning[1],
                        object.getObjectComment(),
                        columns));
            }

            if (result.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "DML AI 초안 작성에 사용할 테이블을 확인할 수 없습니다. 업무 DB와 메타데이터 조회 권한을 확인하세요.");
            }

            return List.copyOf(result);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "DML AI 초안 작성에 필요한 테이블 구조를 조회하지 못했습니다. DB 연결 상태를 확인하세요.",
                    exception);
        }
    }

    /** 실제 존재하는 MySQL 컬럼에만 논리명과 설명을 연결한다. */
    private List<AiSchemaObject.Column> loadColumns(String schemaName, String objectName,
            Map<String, AiViewColumnMetadataService.ColumnMeaning> meanings) {
        return metadataMapper.selectColumns(schemaName, objectName).stream()
                .map(column -> {
                    String[] comment = splitComment(column.getColumnComment());
                    var registered = meanings.get(column.getColumnName());

                    String logicalName = registered == null
                            ? null : trimToNull(registered.logicalName());
                    String description = registered == null
                            ? null : trimToNull(registered.description());

                    return new AiSchemaObject.Column(
                            column.getColumnName(),
                            column.getDataType(),
                            logicalName == null ? comment[0] : logicalName,
                            description == null ? comment[1] : description);
                })
                .toList();
    }

    /** 구분자가 없는 일반 COMMENT는 논리명으로 단정하지 않고 설명으로 사용한다. */
    private static String[] splitComment(String value) {
        String comment = trimToNull(value);
        if (comment == null) return new String[] { null, null };

        int separator = comment.indexOf('|');
        if (separator < 0) return new String[] { null, comment };

        return new String[] {
                trimToNull(comment.substring(0, separator)),
                trimToNull(comment.substring(separator + 1))
        };
    }

    private String currentDatabase() {
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
        return databaseName;
    }

    private static void requireUser(Long userId, Long deptId) {
        if (userId == null || deptId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 사용자 정보를 확인할 수 없습니다.");
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}