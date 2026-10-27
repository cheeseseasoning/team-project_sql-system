package com.sqlflow.access.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.domain.DataAccessPolicy;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.execution.domain.QueryObjectName;

import lombok.RequiredArgsConstructor;

/**
 * SQL의 실제 조회 대상에 대해 요청자의 접근 자격을 검사한다.
 * 결재 처리와 DB_APPROVER 실행 권한 검사는 별도로 수행한다.
 */
@Service
@RequiredArgsConstructor
public class DataAccessValidationService {

    private static final String GENERAL = "GENERAL";
    private static final String SENSITIVE = "SENSITIVE";
    private static final String SENSITIVE_ROLE = "SENSITIVE_DATA_READER";

    private final DataAccessPolicyMapper policyMapper;
    private final UserAccountMapper userAccountMapper;

    /**
     * 요청자의 현재 부서 정책과 역할을 검사한다.
     *
     * @param requesterId 서버에서 확인한 요청자 ID
     * @param targets SQL 메타데이터 검증이 반환한 실제 조회 대상
     * @return 일반·민감 분류와 검증된 리소스 ID
     */
    public ValidationResult validate(Long requesterId, Set<QueryObjectName> targets) {

        if (requesterId == null || requesterId <= 0) {
            throw new IllegalArgumentException("올바른 요청자 ID가 필요합니다.");
        }

        validateTargets(targets);

        UserAccount requester = policyMapper.selectRequester(requesterId);

        if (requester == null
                || !"Y".equals(requester.getEnabled())
                || requester.getDeptId() == null) {
            throw forbidden("활성 계정과 소속 부서를 확인할 수 없습니다.");
        }

        List<DataAccessPolicy> policies = policyMapper.selectPolicies(requester.getDeptId(), targets);

        Map<QueryObjectName, DataAccessPolicy> policiesByTarget = indexPolicies(policies);

        boolean sensitive = false;
        Set<Long> resourceIds = new LinkedHashSet<>();

        for (QueryObjectName target : targets) {
            DataAccessPolicy policy = policiesByTarget.get(target);

            // 대상 미등록·비활성·부서 정책 없음은 모두 허용하지 않는다.
            if (policy == null) {
                throw forbidden("소속 부서에 허용된 조회 대상이 아닙니다: " + displayName(target));
            }
            if (!"ALLOW".equals(policy.getAccessDecision())) {
                throw forbidden("소속 부서에 차단된 조회 대상입니다: " + displayName(target));
            }

            if (SENSITIVE.equals(policy.getAccessLevel())) {
                sensitive = true;
            } else if (!GENERAL.equals(policy.getAccessLevel())) {
                // 잘못된 정책 값을 일반 조회로 간주하지 않는다.
                throw invalidPolicy("조회 수준이 올바르지 않습니다: " + displayName(target));
            }

            resourceIds.add(policy.getResourceId());
        }

        if (sensitive) {
            // 로그인 이후의 역할 변경도 반영하도록 현재 역할을 조회한다.
            List<String> currentRoles = userAccountMapper.selectRoleCodes(requesterId);

            if (!currentRoles.contains(SENSITIVE_ROLE)) {
                throw forbidden("민감 데이터 조회 권한이 없습니다.");
            }
        }

        return new ValidationResult(sensitive ? SENSITIVE : GENERAL, resourceIds);
    }

    private void validateTargets(Set<QueryObjectName> targets) {

        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("검증된 조회 대상이 필요합니다.");
        }

        for (QueryObjectName target : targets) {
            if (target == null
                    || target.owner() == null
                    || target.owner().isBlank()
                    || target.objectName() == null
                    || target.objectName().isBlank()) {
                throw new IllegalArgumentException("조회 대상의 스키마와 객체명이 필요합니다.");
            }
        }
    }

    /**
     * 같은 대상·부서에 활성 정책이 중복되면 임의로 선택하지 않는다.
     * 정책을 정리한 뒤 다시 요청하도록 차단한다.
     */
    private Map<QueryObjectName, DataAccessPolicy> indexPolicies(List<DataAccessPolicy> policies) {

        Map<QueryObjectName, DataAccessPolicy> result = new HashMap<>();

        for (DataAccessPolicy policy : policies) {
        	QueryObjectName target = new QueryObjectName(policy.getOwner(), policy.getObjectName());

            if (policy.getResourceId() == null || policy.getPolicyId() == null) {
                throw invalidPolicy("리소스 또는 정책 ID가 없습니다.");
            }

            if (result.putIfAbsent(target, policy) != null) {
                throw invalidPolicy("활성 접근 정책이 중복되어 있습니다: " + displayName(target));
            }
        }

        return result;
    }

    private String displayName(QueryObjectName target) {
        return target.owner() + "." + target.objectName();
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private ResponseStatusException invalidPolicy(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    /**
     * 접근 검사를 통과한 경우에만 반환된다.
     * resourceIds는 이후 QUERY_RESOURCE_USAGE 저장에 사용한다.
     */
    public record ValidationResult(String accessLevel, Set<Long> resourceIds) {

        public ValidationResult {
            resourceIds = Set.copyOf(resourceIds);
        }
    }
}
