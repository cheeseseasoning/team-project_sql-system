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
import com.sqlflow.access.dto.ViewDepartmentPolicyInfoDTO;
import com.sqlflow.access.dto.ViewResourceInfoDTO;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.mysql.dto.MySqlViewColumnDTO;
import com.sqlflow.mysql.dto.MySqlViewDefinitionDTO;
import com.sqlflow.mysql.dto.MySqlViewSummaryDTO;
import com.sqlflow.mysql.mapper.MySqlViewMapper;
import com.sqlflow.view.dto.ViewDetailResponseDTO;
import com.sqlflow.view.dto.ViewSummaryResponseDTO;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ViewQueryService {

    private final MySqlViewMapper mySqlViewMapper;
    private final ViewResourceMapper viewResourceMapper;
    private final DataAccessPolicyMapper dataAccessPolicyMapper;

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
            ResourceKey key = new ResourceKey(view.getSchemaName(), view.getObjectName());

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
    
    /**
     * 실제 뷰를 기준으로 등록 정보·부서 정책·컬럼·CREATE문을 연결한다.
     * Oracle에 등록되지 않은 뷰도 상세를 확인할 수 있다.
     */
    public ViewDetailResponseDTO getViewDetail(String schemaName, String objectName, Long actorId) {

        UserAccount viewer = requireCurrentViewer(actorId);

        validateViewName(schemaName, objectName);

        // Oracle 등록 여부와 별개로 실제 업무 DB의 뷰인지 먼저 확인한다.
        if (mySqlViewMapper.countView(schemaName, objectName) != 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "현재 업무 DB에서 해당 뷰를 찾을 수 없습니다.");
        }

        ViewResourceInfoDTO resource = findRegisteredResource(schemaName, objectName);

        List<MySqlViewColumnDTO> columns = mySqlViewMapper.selectViewColumns(schemaName, objectName);

        MySqlViewDefinitionDTO definition = mySqlViewMapper.selectViewDefinition(schemaName, objectName);

        // 조회 도중 뷰가 변경되거나 정의를 읽지 못한 경우를 확인한다.
        if (columns == null || columns.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "뷰의 컬럼 정보를 확인할 수 없습니다. 다시 조회해 주세요.");
        }

        if (definition == null
                || definition.getCreateStatement() == null
                || definition.getCreateStatement().isBlank()) {

            throw new ResponseStatusException(HttpStatus.CONFLICT, "뷰의 CREATE문을 확인할 수 없습니다.");
        }

        List<ViewDepartmentPolicyInfoDTO> policies = selectVisiblePolicies(resource, viewer);

        return new ViewDetailResponseDTO(
                schemaName,
                objectName,
                resource == null ? null : resource.getResourceId(),
                resource == null ? null : resource.getResourceName(),
                resource == null ? null : resource.getDescription(),
                resource != null,
                resource == null ? null : resource.getEnabled(),
                List.copyOf(policies),
                List.copyOf(columns),
                definition.getCreateStatement());
    }
    
    /**
     * 세션의 과거 부서 정보 대신 현재 DB의 계정·소속을 확인한다.
     * 비활성 계정이나 유효한 소속이 없는 계정은 상세 조회를 차단한다.
     */
    private UserAccount requireCurrentViewer(Long actorId) {
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        UserAccount viewer = dataAccessPolicyMapper.selectRequester(actorId);

        if (viewer == null || !"Y".equals(viewer.getEnabled()) || viewer.getDeptId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 계정과 소속 부서를 확인할 수 없습니다.");
        }

        ViewDepartmentInfoDTO department = viewResourceMapper.selectDepartments()
                .stream()
                .filter(item -> Objects.equals(item.getDeptId(), viewer.getDeptId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "현재 소속 부서를 확인할 수 없습니다."));

        // 조회한 객체에 현재 부서명을 연결할 뿐 DB를 수정하지 않는다.
        viewer.setDeptName(department.getDeptName());

        return viewer;
    }

    /**
     * 현재 활성 DB팀 사용자는 전체 정책을 조회한다.
     * 그 외 사용자는 역할과 관계없이 현재 본인 부서 정책만 조회한다.
     */
    private List<ViewDepartmentPolicyInfoDTO> selectVisiblePolicies(ViewResourceInfoDTO resource, UserAccount viewer) {

        List<ViewDepartmentPolicyInfoDTO> policies =
                resource == null
                        ? createUnregisteredPolicies()
                        : viewResourceMapper.selectDepartmentPolicies(resource.getResourceId());

        if ("DB팀".equals(viewer.getDeptName())) {
            return policies;
        }

        return policies.stream()
                .filter(policy -> Objects.equals(policy.getDeptId(), viewer.getDeptId()))
                .toList();
    }

    /** 식별자는 임의로 다듬지 않고, 누락 여부만 검사한다. */
    private void validateViewName(String schemaName, String objectName) {

        if (schemaName == null || schemaName.isBlank()
                || objectName == null || objectName.isBlank()) {

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "스키마명과 뷰 이름이 필요합니다.");
        }
    }

    /**
     * 스키마와 객체 이름이 모두 일치하는 등록 정보를 찾는다.
     * 중복 등록은 임의로 하나를 선택하지 않는다.
     */
    private ViewResourceInfoDTO findRegisteredResource(String schemaName, String objectName) {

        List<ViewResourceInfoDTO> matches =
                viewResourceMapper.selectResources(schemaName)
                        .stream()
                        .filter(resource -> Objects.equals(resource.getObjectName(), objectName))
                        .toList();

        if (matches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 객체의 리소스 등록이 중복되어 있습니다.");
        }

        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * 미등록 뷰도 전체 부서를 표시할 수 있도록 빈 정책 정보를 만든다.
     * policyId가 null이면 화면에서 '정책 미등록'으로 표시한다.
     */
    private List<ViewDepartmentPolicyInfoDTO> createUnregisteredPolicies() {
        return viewResourceMapper.selectDepartments()
                .stream()
                .map(department -> {
                    ViewDepartmentPolicyInfoDTO policy = new ViewDepartmentPolicyInfoDTO();

                    policy.setDeptId(department.getDeptId());
                    policy.setDeptName(department.getDeptName());

                    return policy;
                })
                .toList();
    }

    private void validateDepartment(Long deptId) {
        if (deptId == null) {
            return;
        }

        if (deptId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 부서 ID가 필요합니다.");
        }

        boolean exists = viewResourceMapper.selectDepartments()
                .stream()
                .anyMatch(dept -> Objects.equals(dept.getDeptId(), deptId));

        if (!exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 부서입니다.");
        }
    }

    private Map<ResourceKey, ViewResourceInfoDTO> loadResources(List<MySqlViewSummaryDTO> views, Long deptId) {

        Map<ResourceKey, ViewResourceInfoDTO> result = new HashMap<>();

        List<String> schemas = views.stream()
                .map(MySqlViewSummaryDTO::getSchemaName)
                .distinct()
                .toList();

        for (String schema : schemas) {
            List<ViewResourceInfoDTO> registered =
                    deptId == null 
                            ? viewResourceMapper.selectResources(schema)
                            : viewResourceMapper.selectResourcesByDepartment(schema, deptId);

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