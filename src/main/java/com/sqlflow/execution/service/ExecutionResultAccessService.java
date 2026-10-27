package com.sqlflow.execution.service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.domain.DataAccessPolicy;
import com.sqlflow.access.mapper.DataAccessPolicyMapper;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.mapper.UserAccountMapper;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.execution.mapper.ExecutionResultMapper;

import lombok.RequiredArgsConstructor;

/**
 * 저장된 실행 결과의 실제 데이터 접근 권한을 검사한다.
 *
 * 실행이력 열람 자격과 파일 상태 검사를 통과한 뒤 호출한다.
 * 원본 SQL을 재실행하거나 업무 DB를 조회하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionResultAccessService {

    private final ExecutionResultMapper executionResultMapper;
    private final DataAccessPolicyMapper dataAccessPolicyMapper;
    private final UserAccountMapper userAccountMapper;

    /**
     * 실제 CSV 미리보기 권한.
     * EXPORT_POLICY는 검사하지 않는다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true
    )
    public void requirePreview(Long actorId, Long queryId) {
        validateAccess(actorId, queryId, false);
    }

    /**
     * CSV 다운로드 권한.
     * 조회 권한과 EXPORT_POLICY를 모두 검사한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true
    )
    public void requireDownload(Long actorId, Long queryId) {
        validateAccess(actorId, queryId, true);
    }

    private void validateAccess(
            Long actorId,
            Long queryId,
            boolean download) {

        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (queryId == null || queryId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "실행 결과의 SQL 정보를 확인할 수 없습니다.");
        }

        // 세션에 남은 정보가 아닌 현재 계정 상태를 확인한다.
        UserAccount actor =
                dataAccessPolicyMapper.selectRequester(actorId);

        if (actor == null || !"Y".equals(actor.getEnabled())) {
            throw forbidden("활성 사용자만 결과에 접근할 수 있습니다.");
        }

        List<String> roles =
                userAccountMapper.selectRoleCodes(actorId);

        // 이번에 정한 관리자 예외.
        // 이력 열람 자격과 파일 만료·삭제 검사를 생략한다는 뜻은 아니다.
        if (roles.contains("DB_ADMIN")
                || roles.contains("DB_APPROVER")) {
            return;
        }

        if (actor.getDeptId() == null) {
            throw forbidden("현재 소속 부서를 확인할 수 없습니다.");
        }

        Set<QueryObjectName> targets = loadTargets(queryId);

        List<DataAccessPolicy> policies =
                dataAccessPolicyMapper.selectPolicies(
                        actor.getDeptId(),
                        targets);

        Map<QueryObjectName, DataAccessPolicy> policiesByTarget =
                indexPolicies(policies);

        boolean sensitive = false;

        // 먼저 모든 대상의 조회 권한을 확인한다.
        for (QueryObjectName target : targets) {
            DataAccessPolicy policy = policiesByTarget.get(target);

            // 미등록·비활성 리소스와 부서 정책 없음도 차단된다.
            if (policy == null) {
                throw forbidden(
                        "현재 소속 부서에 허용되지 않은 데이터가 포함되어 있습니다.");
            }

            if (!"ALLOW".equals(policy.getAccessDecision())) {
                throw forbidden("현재 소속 부서에 차단된 데이터가 포함되어 있습니다.");
            }

            if ("SENSITIVE".equals(policy.getAccessLevel())) {
                sensitive = true;

            } else if (!"GENERAL".equals(policy.getAccessLevel())) {
                throw invalidPolicy(
                        "데이터 접근 수준 설정을 확인해야 합니다.");
            }
        }

        if (sensitive && !roles.contains("SENSITIVE_DATA_READER")) {
            throw forbidden("민감 데이터 조회 권한이 없습니다.");
        }

        // 미리보기에서는 여기서 종료한다.
        if (!download) {
            return;
        }

        // 다운로드에만 반출 정책을 추가 적용한다.
        for (QueryObjectName target : targets) {
            String exportPolicy =
                    policiesByTarget.get(target).getExportPolicy();

            if ("BLOCK".equals(exportPolicy)) {
                throw forbidden(
                        "CSV 다운로드가 제한된 결과입니다. "
                        + "조회 권한이 있으면 미리보기는 이용할 수 있습니다.");
            }

            // 누락·오타 등 알 수 없는 값을 허용으로 취급하지 않는다.
            if (!"ALLOW".equals(exportPolicy)) {
                throw invalidPolicy(
                        "CSV 반출 정책 설정을 확인해야 합니다.");
            }
        }
    }

    private Set<QueryObjectName> loadTargets(Long queryId) {
        List<QueryObjectName> storedTargets =
                executionResultMapper.selectResultTargets(queryId);

        if (storedTargets == null || storedTargets.isEmpty()) {
            throw forbidden(
                    "저장된 조회 대상 정보가 없어 결과 접근을 허용할 수 없습니다.");
        }

        Set<QueryObjectName> targets = new LinkedHashSet<>();

        for (QueryObjectName target : storedTargets) {
            if (target == null
                    || target.owner() == null
                    || target.owner().isBlank()
                    || target.objectName() == null
                    || target.objectName().isBlank()) {
                throw forbidden(
                        "저장된 조회 대상 정보를 확인할 수 없습니다.");
            }

            targets.add(target);
        }

        return targets;
    }

    private Map<QueryObjectName, DataAccessPolicy> indexPolicies(
            List<DataAccessPolicy> policies) {

        Map<QueryObjectName, DataAccessPolicy> result =
                new HashMap<>();

        for (DataAccessPolicy policy : policies) {
            if (policy.getResourceId() == null
                    || policy.getPolicyId() == null
                    || policy.getOwner() == null
                    || policy.getObjectName() == null) {
                throw invalidPolicy(
                        "데이터 접근 정책 정보가 올바르지 않습니다.");
            }

            QueryObjectName target = new QueryObjectName(
                    policy.getOwner(),
                    policy.getObjectName());

            if (result.putIfAbsent(target, policy) != null) {
                throw invalidPolicy(
                        "동일 조회 대상의 활성 접근 정책이 중복되어 있습니다.");
            }
        }

        return result;
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                message);
    }

    private ResponseStatusException invalidPolicy(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message);
    }
}
