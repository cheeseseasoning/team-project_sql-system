package com.sqlflow.approval.service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.domain.ApprovalActionContext;
import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.domain.PendingApproval;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.ApprovalCandidatesResponse;
import com.sqlflow.approval.dto.ApprovalHistoryResponse;
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
import com.sqlflow.request.dto.ApprovalTargetRequest;
import com.sqlflow.request.dto.RequestDetailResponse;
import com.sqlflow.request.service.ApprovalLineCreator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalService implements ApprovalLineCreator {

    private final ApprovalMapper approvalMapper;

    @Transactional(readOnly = true)
    public List<PendingApprovalResponse> getPendingApprovals(Long approverId) {
        return approvalMapper.selectPendingApprovals(approverId);
    }

    @Transactional(readOnly = true)
    public RequestDetailResponse getPendingApprovalDetail(
            Long requestId,
            Long approverId) {
        RequestDetailResponse detail =
                approvalMapper.selectPendingApprovalDetail(requestId, approverId);

        if (detail == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "결재 대기 중인 요청을 찾을 수 없습니다.");
        }

        detail.setItems(
                approvalMapper.selectPendingApprovalItems(requestId, approverId));
        return detail;
    }
    
    @Transactional(readOnly = true)
    public List<ApprovalHistoryResponse> getApprovalHistory(
            Long requestId,
            Long actorId) {
        if (approvalMapper.canViewApprovalHistory(requestId, actorId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        return approvalMapper.selectApprovalHistory(requestId);
    }

    @Transactional
    public void approve(Long requestId, Long actorId) {
        ApprovalActionContext request =
                approvalMapper.selectApprovalRequestForUpdate(requestId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        if (!"PENDING_APPROVAL".equals(request.getRequestStatus())
                || !"PENDING".equals(request.getApprovalStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 대기 중인 요청이 아닙니다.");
        }

        PendingApproval pending =
                approvalMapper.selectPendingApprovalForUpdate(requestId, actorId);

        if (pending == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 결재 권한이 없습니다.");
        }

        boolean supportedApprovalType =
                "APPROVER".equals(pending.getApprovalType())
                        || "INTERMEDIATE".equals(pending.getApprovalType())
                        || "DIRECTOR".equals(pending.getApprovalType())
                        || "DB_EXECUTION".equals(pending.getApprovalType());

        if (!supportedApprovalType) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "지원하지 않는 결재 단계입니다.");
        }

        if (approvalMapper.approvePendingApproval(
                pending.getApprovalId(), actorId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 상태가 변경되었습니다. 다시 확인하세요.");
        }

        int activated = approvalMapper.activateNextApproval(requestId);

        // 결재 유형과 관계없이 다음 순서의 대기 결재를 활성화한다.
        if (activated == 1) {
            return;
        }

        // 다음 단계가 없으면, 모든 결재가 승인됐는지 SQL에서 확인한다.
        if (activated != 0
                || approvalMapper.markRequestApproved(requestId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 진행 상태를 확인할 수 없습니다. 다시 확인하세요.");
        }
    }
    
    
    @Transactional
    public void reject(Long requestId, Long actorId, String commentText) {
        if (commentText == null || commentText.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "반려 의견을 입력하세요.");
        }

        ApprovalActionContext request =
                approvalMapper.selectApprovalRequestForUpdate(requestId);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        if (!"PENDING_APPROVAL".equals(request.getRequestStatus())
                || !"PENDING".equals(request.getApprovalStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 대기 중인 요청이 아닙니다.");
        }

        PendingApproval pending =
                approvalMapper.selectPendingApprovalForUpdate(requestId, actorId);

        if (pending == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 결재 권한이 없습니다.");
        }

        boolean supportedApprovalType =
                "APPROVER".equals(pending.getApprovalType())
                        || "INTERMEDIATE".equals(pending.getApprovalType())
                        || "DIRECTOR".equals(pending.getApprovalType())
                        || "DB_EXECUTION".equals(pending.getApprovalType());

        if (!supportedApprovalType) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "지원하지 않는 결재 단계입니다.");
        }

        if (approvalMapper.rejectPendingApproval(
                pending.getApprovalId(),
                actorId,
                commentText.strip()) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "결재 상태가 변경되었습니다. 다시 확인하세요.");
        }

        // 기존 결재 이력은 보존하고 후속 대기 결재만 취소
        approvalMapper.cancelWaitingApprovals(requestId);

        if (approvalMapper.markRequestRejected(requestId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "요청 상태를 반려로 변경할 수 없습니다.");
        }
    }

    
    @Transactional(readOnly = true)
    public ApprovalCandidatesResponse getApprovalCandidates(
            Long requestId,
            Long actorId,
            String keyword) {

        // 본인이 작성한 초안에서만 결재자를 조회한다.
        requireOwnedDraft(requestId, actorId);

        String normalizedKeyword =
                keyword == null || keyword.isBlank()
                        ? null
                        : keyword.trim();

        List<ApprovalCandidateResponse> candidates =
                approvalMapper.searchActiveCandidates(normalizedKeyword, actorId);

        // 이름 검색으로 직원 일부만 조회돼도 부서의 결재 가능 인원은 전체 기준을 유지한다.
        Set<Long> matchingDepartmentIds = new HashSet<>();
        for (ApprovalCandidateResponse candidate : candidates) {
            matchingDepartmentIds.add(candidate.getDeptId());
        }

        String searchWord = normalizedKeyword == null ? "" : normalizedKeyword.toLowerCase(Locale.ROOT);
        var departments = approvalMapper.searchDepartmentCandidates(null, actorId).stream()
                .filter(department -> searchWord.isEmpty()
                        || department.getDeptName().toLowerCase(Locale.ROOT).contains(searchWord)
                        || matchingDepartmentIds.contains(department.getDeptId()))
                .toList();

        return new ApprovalCandidatesResponse(candidates, departments);
    }
    
    @Transactional(readOnly = true)
    public List<ApprovalCandidateResponse> searchApprovalCandidates(
            String keyword,
            Long actorId) {
        String normalizedKeyword =
                keyword == null || keyword.isBlank()
                        ? null
                        : keyword.trim();

        return approvalMapper.searchActiveCandidates(
                normalizedKeyword,
                actorId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createApprovalLine(
            Long requestId,
            Long actorId,
            List<Long> approverIds) {

        requireOwnedDraft(requestId, actorId);

        if (approverIds == null || approverIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "결재자를 1명 이상 지정하세요.");
        }

        Set<Long> uniqueIds = new HashSet<>();

        // 저장하기 전에 목록 전체의 형식·본인 포함·중복을 확인한다.
        for (Long approverId : approverIds) {
            if (approverId == null || approverId <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "올바른 결재자를 선택하세요.");
            }

            if (approverId.equals(actorId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "본인을 결재자로 지정할 수 없습니다.");
            }

            if (!uniqueIds.add(approverId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "동일한 결재자를 중복 지정할 수 없습니다.");
            }
        }

        Set<Long> activeIds = new HashSet<>();

        // 제출 시점의 실제 활성 사용자 목록으로 다시 확인한다.
        for (ApprovalCandidateResponse candidate :
                approvalMapper.searchActiveCandidates(null, actorId)) {
            activeIds.add(candidate.getUserId());
        }

        if (!activeIds.containsAll(uniqueIds)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "선택한 결재자 중 사용할 수 없는 계정이 있습니다. "
                            + "결재선을 다시 확인하세요.");
        }

        // 요청자가 지정한 순서를 그대로 보존한다.
        for (int index = 0; index < approverIds.size(); index++) {
            int inserted = approvalMapper.insertApproval(
                    requestId,
                    approverIds.get(index),
                    index + 1,
                    "APPROVER",
                    index == 0 ? "PENDING" : "WAITING");

            if (inserted != 1) {
                throw new IllegalStateException(
                        "결재선 저장에 실패했습니다.");
            }
        }
    }
    
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createApprovalLineWithTargets(Long requestId, Long actorId, List<ApprovalTargetRequest> targets) {

        requireOwnedDraft(requestId, actorId);

        if (targets == null || targets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "결재 대상을 1개 이상 지정하세요.");
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> departmentIds = new HashSet<>();

        // 저장 전에 전체 결재선의 형식·본인 지정·대상 중복을 검사한다.
        for (ApprovalTargetRequest target : targets) {
            if (target == null || !target.isTargetValid()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "개인 또는 부서 결재 대상을 올바르게 지정하세요.");
            }

            if ("USER".equals(target.getTargetType())) {
                Long userId = target.getApproverId();

                if (userId <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "올바른 결재자를 선택하세요.");
                }

                if (userId.equals(actorId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "본인을 결재자로 지정할 수 없습니다.");
                }

                if (!userIds.add(userId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "동일한 결재자를 중복 지정할 수 없습니다.");
                }
            } else {
                Long deptId = target.getApproverDeptId();

                if (deptId <= 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "올바른 담당 부서를 선택하세요.");
                }

                if (!departmentIds.add(deptId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "동일한 부서를 중복 지정할 수 없습니다.");
                }
            }
        }

        if (!userIds.isEmpty()) {
            Set<Long> activeUserIds = new HashSet<>();

            // 기존 개인 결재 후보 조회를 재사용한다.
            for (ApprovalCandidateResponse candidate :
                    approvalMapper.searchActiveCandidates(null, actorId)) {
                activeUserIds.add(candidate.getUserId());
            }

            if (!activeUserIds.containsAll(userIds)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "선택한 결재자 중 사용할 수 없는 계정이 있습니다. 결재선을 다시 확인하세요.");
            }
        }

        if (!departmentIds.isEmpty()) {
            Set<Long> availableDepartmentIds = new HashSet<>();

            // 모든 부서를 대상으로 기안자 외 활성 구성원이 있는지 확인한다.
            for (var department : approvalMapper.searchDepartmentCandidates(null, actorId)) {
                Integer count = department.getEligibleUserCount();

                if (count != null && count > 0) {
                    availableDepartmentIds.add(department.getDeptId());
                }
            }

            if (!availableDepartmentIds.containsAll(departmentIds)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "선택한 부서가 없거나 기안자 외 결재 가능한 활성 사용자가 없습니다. 결재선을 다시 확인하세요.");
            }
        }

        // 모든 검증이 끝난 뒤 선택 순서대로 개인·부서 결재 단계를 저장한다.
        for (int index = 0; index < targets.size(); index++) {
            ApprovalTargetRequest target = targets.get(index);

            int inserted = approvalMapper.insertApprovalTarget(
                    requestId,
                    target.getTargetType(),
                    target.getApproverId(),
                    target.getApproverDeptId(),
                    index + 1,
                    "APPROVER",
                    index == 0 ? "PENDING" : "WAITING");

            if (inserted != 1) {
                throw new IllegalStateException("결재선 저장에 실패했습니다.");
            }
        }
    }

    private ApprovalRequestContext requireOwnedDraft(
            Long requestId,
            Long actorId) {
        ApprovalRequestContext request =
                approvalMapper.selectRequestContext(requestId);

        if (request == null || !request.getDrafterId().equals(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다.");
        }

        if (!"DRAFT".equals(request.getRequestStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "초안 상태의 요청만 결재자를 조회하거나 제출할 수 있습니다.");
        }

        return request;
    }
}