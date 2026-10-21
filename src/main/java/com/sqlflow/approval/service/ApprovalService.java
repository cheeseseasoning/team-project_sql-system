package com.sqlflow.approval.service;

import java.util.HashSet;
import java.util.List;
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
import com.sqlflow.approval.dto.PendingApprovalResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
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
                "INTERMEDIATE".equals(pending.getApprovalType())
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

        // DB팀 실행 승인 단계가 최종 승인
        if ("DB_EXECUTION".equals(pending.getApprovalType())) {
            if (approvalMapper.markRequestApproved(requestId) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "요청 상태를 최종 승인으로 변경할 수 없습니다.");
            }
            return;
        }

        // 중간 결재 또는 부서장 결재 후 다음 순서의 결재자 한 명을 활성화
        if (approvalMapper.activateNextApproval(requestId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "다음 결재 단계를 활성화할 수 없습니다.");
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
                "INTERMEDIATE".equals(pending.getApprovalType())
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
            Long actorId) {
        ApprovalRequestContext request = requireOwnedDraft(requestId, actorId);

        List<ApprovalCandidateResponse> departmentApprovers =
                approvalMapper.selectDepartmentApprovers(request.getDeptId())
                        .stream()
                        .toList();

        if (departmentApprovers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "소속 부서장이 여러 명이라 결재자를 확정할 수 없습니다.");
        }

        ApprovalCandidateResponse departmentApprover =
                departmentApprovers.size() == 1
                        ? departmentApprovers.get(0)
                        : null;

        List<ApprovalCandidateResponse> dbApprovers =
                departmentApprover == null
                        ? List.of()
                        : approvalMapper
                                .selectDbApprovers(departmentApprover.getUserId())
                                .stream()
                                .filter(candidate ->
                                        !candidate.getUserId().equals(actorId))
                                .toList();

        return new ApprovalCandidatesResponse(
                departmentApprover,
                dbApprovers);
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
            List<Long> intermediateApproverIds,
            Long dbApproverId) {
        ApprovalRequestContext request = requireOwnedDraft(requestId, actorId);

        // 요청의 DEPT_ID 기준으로 부서장을 찾음
        // 요청자 본인이 부서장일 수 있으므로 요청자 제외 필터를 적용하지 않음
        List<ApprovalCandidateResponse> departmentApprovers =
                approvalMapper.selectDepartmentApprovers(request.getDeptId());

        if (departmentApprovers == null || departmentApprovers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "활성 상태의 소속 부서장이 없어 제출할 수 없습니다.");
        }

        if (departmentApprovers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "소속 부서장이 여러 명이라 결재자를 확정할 수 없습니다.");
        }

        Long departmentApproverId = departmentApprovers.get(0).getUserId();

        List<Long> intermediateIds = intermediateApproverIds == null
                ? List.of()
                : intermediateApproverIds;

        if (dbApproverId == null
                || dbApproverId.equals(actorId)
                || dbApproverId.equals(departmentApproverId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청자 및 부서장과 다른 DB팀 실행 승인자를 선택하세요.");
        }

        // 중간 결재자는 활성 사용자여야 하며, 중복·본인·필수 결재자와의 중복을 금지
        Set<Long> uniqueIntermediateIds = new HashSet<>();
        List<ApprovalCandidateResponse> activeCandidates =
                approvalMapper.searchActiveCandidates(null, actorId);

        for (Long approverId : intermediateIds) {
            boolean isActiveUser = approverId != null
                    && activeCandidates.stream()
                            .anyMatch(candidate ->
                                    candidate.getUserId().equals(approverId));

            if (approverId == null
                    || !uniqueIntermediateIds.add(approverId)
                    || approverId.equals(actorId)
                    || approverId.equals(departmentApproverId)
                    || approverId.equals(dbApproverId)
                    || !isActiveUser) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "중간 결재자는 활성 사용자 중에서 중복 없이 선택해야 합니다.");
            }
        }

        boolean isEligibleDbApprover =
                approvalMapper.selectDbApprovers(departmentApproverId)
                        .stream()
                        .anyMatch(candidate ->
                                candidate.getUserId().equals(dbApproverId)
                                        && !candidate.getUserId().equals(actorId));

        if (!isEligibleDbApprover) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "선택한 사용자는 현재 활성 DB팀 실행 승인 자격이 없습니다.");
        }

        int approvalOrder = 1;

        // 중간 결재자를 전달된 순서대로 생성
        for (Long approverId : intermediateIds) {
            approvalMapper.insertApproval(
                    requestId,
                    approverId,
                    approvalOrder,
                    "INTERMEDIATE",
                    approvalOrder == 1 ? "PENDING" : "WAITING");
            approvalOrder++;
        }

        // 부서장은 본인 요청이어도 결재할 수 있음
        approvalMapper.insertApproval(
                requestId,
                departmentApproverId,
                approvalOrder,
                "DIRECTOR",
                approvalOrder == 1 ? "PENDING" : "WAITING");
        approvalOrder++;

        // DB팀 실행 승인자는 항상 마지막이며, 앞 단계가 끝나면 활성화
        approvalMapper.insertApproval(
                requestId,
                dbApproverId,
                approvalOrder,
                "DB_EXECUTION",
                "WAITING");
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