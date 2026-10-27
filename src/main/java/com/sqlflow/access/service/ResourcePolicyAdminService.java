package com.sqlflow.access.service;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.access.dto.ViewDepartmentPolicyInfoDTO;
import com.sqlflow.access.dto.ViewResourceInfoDTO;
import com.sqlflow.access.mapper.ViewResourceMapper;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResourcePolicyAdminService {
    private final ViewResourceMapper resourceMapper;
    private final MySqlMetadataMapper mysqlMapper;
    private final UserAccountMapper userMapper;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long userId) {
        requireDbTeam(userId);
        List<ViewResourceInfoDTO> resources = resourceMapper.selectAllManagedResources();
        return mysqlMapper.selectSelectableObjects().stream().map(object -> {
            ViewResourceInfoDTO resource = resources.stream().filter(r -> r.getSchemaName().equals(object.getDatabaseName())
                    && r.getObjectName().equals(object.getObjectName())).findFirst().orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schemaName", object.getDatabaseName());
            row.put("objectName", object.getObjectName());
            row.put("objectType", object.getObjectType());
            row.put("description", object.getObjectComment() == null ? "" : object.getObjectComment());
            row.put("resourceId", resource == null ? 0L : resource.getResourceId());
            row.put("resourceEnabled", resource != null && "Y".equals(resource.getEnabled()));
            row.put("accessLevel", resource == null ? null : resource.getAccessLevel());
            row.put("exportPolicy", resource == null ? null : resource.getExportPolicy());
            row.put("policies", resource == null ? List.of() : resourceMapper.selectActiveDepartmentMemberships(resource.getResourceId()));
            return row;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ViewDepartmentInfoDTO> departments(Long userId) {
        requireDbTeam(userId);
        return resourceMapper.selectDepartments();
    }

    public void assertCanView(Long userId) { requireDbTeam(userId); }

    @Transactional
    public Map<String, Object> register(Long userId, ResourceRequest request) {
        requireDbTeam(userId);
        if (request == null || blank(request.schemaName()) || blank(request.objectName())) throw badRequest("조회 대상을 선택하세요.");
        String currentDatabase = mysqlMapper.selectCurrentDatabase();
        if (!request.schemaName().equals(currentDatabase)) throw badRequest("현재 연결된 MySQL 데이터베이스의 객체만 등록할 수 있습니다.");
        MySqlObjectMetadata object = mysqlMapper.selectObject(request.schemaName(), request.objectName());
        if (object == null || !object.getDatabaseName().equals(request.schemaName())
                || !List.of("BASE TABLE", "VIEW").contains(object.getObjectType())) throw badRequest("현재 MySQL에 존재하는 테이블·뷰만 등록할 수 있습니다.");
        if (resourceMapper.countResource(object.getDatabaseName(), object.getObjectName()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 등록된 리소스입니다.");
        }
        String name = blank(request.resourceName()) ? object.getObjectName() : request.resourceName().trim();
        String description = request.description() == null ? "" : request.description().trim();
        if (name.length() > 200 || description.length() > 1000) throw badRequest("리소스 이름 또는 설명이 너무 깁니다.");
        Long id = resourceMapper.nextResourceId();
        resourceMapper.insertResource(id, object.getDatabaseName(), object.getObjectName(), name, description);
        return Map.of("resourceId", id, "message", "조회 리소스를 등록했습니다.");
    }

    @Transactional
    public Map<String, Object> savePolicy(Long userId, Long resourceId, PolicyRequest request) {
        requireAdmin(userId);
        if (request == null) throw badRequest("정책 값을 입력하세요.");
        if (!"GENERAL".equals(request.accessLevel()) && !"SENSITIVE".equals(request.accessLevel())) throw badRequest("조회 수준을 선택하세요.");
        if (!"ALLOW".equals(request.exportPolicy()) && !"BLOCK".equals(request.exportPolicy())) throw badRequest("반출 정책을 선택하세요.");
        if (!"Y".equals(request.enabled()) && !"N".equals(request.enabled())) throw badRequest("리소스 상태를 선택하세요.");
        if (resourceMapper.updateResourcePolicy(resourceId, request.accessLevel(), request.exportPolicy(), request.enabled()) != 1) throw badRequest("등록된 리소스를 찾을 수 없습니다.");
        return Map.of("message", "리소스 정책을 저장했습니다.");
    }

    @Transactional
    public Map<String, Object> saveDepartments(Long userId, Long resourceId, DepartmentRequest request) {
        requireAdmin(userId);
        if (request == null || request.deptIds() == null) throw badRequest("접근 부서를 입력하세요.");
        ViewResourceInfoDTO resource = resourceMapper.selectAllManagedResources().stream()
                .filter(r -> r.getResourceId().equals(resourceId)).findFirst()
                .orElseThrow(() -> badRequest("등록된 리소스를 찾을 수 없습니다."));
        Set<Long> deptIds = new LinkedHashSet<>(request.deptIds());
        Set<Long> validIds = resourceMapper.selectDepartments().stream().map(ViewDepartmentInfoDTO::getDeptId)
                .collect(java.util.stream.Collectors.toSet());
        if (!validIds.containsAll(deptIds)) throw badRequest("유효하지 않은 부서가 포함되어 있습니다.");
        if (deptIds.isEmpty()) resourceMapper.deleteDepartmentPolicies(resourceId);
        else resourceMapper.deleteDepartmentPoliciesNotIn(resourceId, List.copyOf(deptIds));
        for (Long deptId : deptIds) {
            Long policyId = resourceMapper.selectPolicyIdForDepartment(resourceId, deptId);
            if (policyId == null) {
                resourceMapper.insertDepartmentMembership(resourceMapper.nextPolicyId(), resourceId, deptId);
            } else {
                resourceMapper.enableDepartmentMembership(policyId);
            }
        }
        return Map.of("message", "접근 부서를 저장했습니다.");
    }

    private void requireDbTeam(Long userId) {
        if (userId == null || resourceMapper.selectActiveDbTeamMember(userId) != 1) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "활성 DB팀 사용자만 접근할 수 있습니다.");
    }
    private void requireAdmin(Long userId) {
        requireDbTeam(userId);
        if (!userMapper.selectRoleCodes(userId).contains("DB_ADMIN")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DB_ADMIN 역할이 필요합니다.");
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    public record ResourceRequest(String schemaName, String objectName, String resourceName, String description) { }
    public record PolicyRequest(String accessLevel, String exportPolicy, String enabled) { }
    public record DepartmentRequest(List<Long> deptIds) { }
}
