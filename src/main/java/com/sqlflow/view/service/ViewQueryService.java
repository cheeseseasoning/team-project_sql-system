package com.sqlflow.view.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.access.dto.ViewResourceInfoDTO;
import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.mysql.dto.MySqlViewSummaryDTO;
import com.sqlflow.mysql.mapper.MySqlViewMapper;
import com.sqlflow.view.dto.ViewSummaryResponseDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ViewQueryService {

    private final MySqlViewMapper mySqlViewMapper;
    private final ViewResourceMapper viewResourceMapper;

    /**
     * 실제 뷰 목록을 기준으로 Oracle 등록 정보를 연결한다.
     * deptId가 null이면 정책 미등록 뷰도 포함한다.
     */
    public List<ViewSummaryResponseDTO> getViews(Long deptId, String keyword) {

        validateDepartment(deptId);

        String searchWord = normalize(keyword);
        List<MySqlViewSummaryDTO> actualViews = mySqlViewMapper.selectViews();

        // 같은 스키마는 한 번만 조회한다.
        Map<ResourceKey, ViewResourceInfoDTO> resources = loadResources(actualViews, deptId);

        List<ViewSummaryResponseDTO> result = new ArrayList<>();

        for (MySqlViewSummaryDTO view : actualViews) {
            ResourceKey key = new ResourceKey(
                    view.getSchemaName(),
                    view.getObjectName());

            ViewResourceInfoDTO resource = resources.get(key);

            // 특정 부서를 선택하면 활성 정책에 연결된 뷰만 남긴다.
            if (deptId != null && resource == null) {
                continue;
            }

            if (!matches(view, resource, searchWord)) {
                continue;
            }

            result.add(new ViewSummaryResponseDTO(
                    view.getSchemaName(),
                    view.getObjectName(),
                    resource == null ? null : resource.getResourceId(),
                    resource == null ? null : resource.getResourceName(),
                    resource == null ? null : resource.getDescription(),
                    resource != null,
                    resource == null ? null : resource.getEnabled()));
        }

        return List.copyOf(result);
    }

    /** 부서 드롭다운에 사용할 목록을 반환한다. */
    public List<ViewDepartmentInfoDTO> getDepartments() {
        return viewResourceMapper.selectDepartments();
    }

    private void validateDepartment(Long deptId) {
        if (deptId == null) {
            return;
        }

        if (deptId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 부서 ID가 필요합니다.");
        }

        boolean exists = viewResourceMapper.selectDepartments()
                .stream()
                .anyMatch(dept ->
                        Objects.equals(dept.getDeptId(), deptId));

        if (!exists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "존재하지 않는 부서입니다.");
        }
    }

    private Map<ResourceKey, ViewResourceInfoDTO> loadResources(
            List<MySqlViewSummaryDTO> views,
            Long deptId) {

        Map<ResourceKey, ViewResourceInfoDTO> result = new HashMap<>();

        List<String> schemas = views.stream()
                .map(MySqlViewSummaryDTO::getSchemaName)
                .distinct()
                .toList();

        for (String schema : schemas) {
            List<ViewResourceInfoDTO> registered =
                    deptId == null
                            ? viewResourceMapper.selectResources(schema)
                            : viewResourceMapper.selectResourcesByDepartment(
                                    schema, deptId);

            for (ViewResourceInfoDTO resource : registered) {
                ResourceKey key = new ResourceKey(resource.getSchemaName(), resource.getObjectName());

                // 중복 등록을 임의로 하나 선택해 표시하지 않는다.
                if (result.putIfAbsent(key, resource) != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 객체의 리소스 등록이 중복되어 있습니다.");
                }
            }
        }

        return result;
    }

    private boolean matches(MySqlViewSummaryDTO view, ViewResourceInfoDTO resource, String keyword) {

        if (keyword.isEmpty()) {
            return true;
        }

        return normalize(view.getObjectName()).contains(keyword)
                || (resource != null && (normalize(resource.getResourceName())
                            .contains(keyword) || normalize(resource.getDescription())
                            .contains(keyword)));
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    /** 스키마와 객체명을 함께 사용해 서로 다른 객체를 구분한다. */
    private record ResourceKey(String schemaName, String objectName) {
    }
}