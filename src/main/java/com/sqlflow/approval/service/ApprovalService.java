package com.sqlflow.approval.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.approval.domain.ApprovalRequestContext;
import com.sqlflow.approval.dto.ApprovalCandidateResponse;
import com.sqlflow.approval.dto.ApprovalCandidatesResponse;
import com.sqlflow.approval.mapper.ApprovalMapper;
import com.sqlflow.request.service.ApprovalLineCreator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalService implements ApprovalLineCreator {

    private final ApprovalMapper approvalMapper;

    @Transactional(readOnly = true)
    public ApprovalCandidatesResponse getApprovalCandidates(
            Long requestId,
            Long actorId) {
        ApprovalRequestContext request = requireOwnedDraft(requestId, actorId);
        List<ApprovalCandidateResponse> departmentApprovers =
                approvalMapper.selectDepartmentApprovers(request.getDeptId())
                        .stream()
                        .filter(candidate -> !candidate.userId().equals(actorId))
                        .toList();

        if (departmentApprovers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "소속 부서장이 여러 명이라 결재자를 확정할 수 없습니다."
            );
        }

        ApprovalCandidateResponse departmentApprover =
                departmentApprovers.size() == 1
                        ? departmentApprovers.get(0)
                        : null;

        List<ApprovalCandidateResponse> dbApprovers = departmentApprover == null
                ? List.of()
                : approvalMapper.selectDbApprovers(departmentApprover.userId())
                        .stream()
                        .filter(candidate -> !candidate.userId().equals(actorId))
                        .toList();

        return new ApprovalCandidatesResponse(departmentApprover, dbApprovers);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void createApprovalLine(
            Long requestId,
            Long actorId,
            Long dbApproverId) {
        ApprovalRequestContext request = requireOwnedDraft(requestId, actorId);
        List<ApprovalCandidateResponse> departmentApprovers =
                approvalMapper.selectDepartmentApprovers(request.getDeptId())
                        .stream()
                        .filter(candidate -> !candidate.userId().equals(actorId))
                        .toList();

        if (departmentApprovers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "활성 상태의 소속 부서장이 없어 제출할 수 없습니다."
            );
        }
        if (departmentApprovers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "소속 부서장이 여러 명이라 결재자를 확정할 수 없습니다."
            );
        }

        Long departmentApproverId = departmentApprovers.get(0).userId();
        if (dbApproverId == null
                || dbApproverId.equals(actorId)
                || dbApproverId.equals(departmentApproverId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청자 및 부서장과 다른 DB팀 결재자를 선택하세요."
            );
        }

        boolean isEligibleDbApprover = approvalMapper
                .selectDbApprovers(departmentApproverId)
                .stream()
                .anyMatch(candidate -> candidate.userId().equals(dbApproverId)
                        && !candidate.userId().equals(actorId));
        if (!isEligibleDbApprover) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "선택한 사용자는 현재 활성 DB_APPROVER 결재자가 아닙니다."
            );
        }

        // 요청 정책: 1단계 부서장, 2단계 선택 DB 결재자.
        // APPROVAL_TYPE은 ERD의 고정 코드(DIRECTOR/INTERMEDIATE)를 사용한다.
        approvalMapper.insertApproval(
                requestId, departmentApproverId, 1, "DIRECTOR", "PENDING");
        approvalMapper.insertApproval(
                requestId, dbApproverId, 2, "INTERMEDIATE", "WAITING");
    }

    private ApprovalRequestContext requireOwnedDraft(
            Long requestId,
            Long actorId) {
        ApprovalRequestContext request =
                approvalMapper.selectRequestContext(requestId);
        if (request == null || !request.getDrafterId().equals(actorId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "조회 요청을 찾을 수 없습니다."
            );
        }
        if (!"DRAFT".equals(request.getRequestStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "초안 상태의 요청만 결재자를 조회하거나 제출할 수 있습니다."
            );
        }
        return request;
    }
}
