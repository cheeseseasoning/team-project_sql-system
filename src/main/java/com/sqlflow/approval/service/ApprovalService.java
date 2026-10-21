package com.sqlflow.approval.service;

import java.util.List;

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

        if (actorId.equals(request.getDrafterId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "요청자는 본인 요청을 결재할 수 없습니다.");
        }

        PendingApproval pending =
                approvalMapper.selectPendingApprovalForUpdate(requestId, actorId);

        if (pending == null) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "현재 결재 권한이 없습니다.");
        }

        boolean departmentApproval =
                Integer.valueOf(1).equals(pending.getApprovalOrder())
                        && "DIRECTOR".equals(pending.getApprovalType());
        boolean finalApproval =
                Integer.valueOf(2).equals(pending.getApprovalOrder())
                        && "INTERMEDIATE".equals(pending.getApprovalType());

        if (!departmentApproval && !finalApproval) {
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

        if (departmentApproval) {
            if (approvalMapper.activateNextApproval(requestId) != 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "다음 결재 단계를 활성화할 수 없습니다.");
            }
            return;
        }

        if (approvalMapper.markRequestApproved(requestId) != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "요청 상태를 최종 승인으로 변경할 수 없습니다.");
        }
    }
    
    
    @Transactional
    public void reject(Long requestId, Long actorId, String commentText) {
    	if(commentText == null || commentText.isBlank()) {
    		throw new ResponseStatusException(
    				HttpStatus.BAD_REQUEST,
    				"반려 의견을 입력하세요.");
    	}
    	
    	ApprovalActionContext request = 
    			approvalMapper.selectApprovalRequestForUpdate(requestId);
    	
    	if(request == null) {
    		throw new ResponseStatusException(
    				HttpStatus.NOT_FOUND,
    				"조회 요청을 찾을 수 없습니다.");
    	}
    	
    	if(!"PENDING_APPROVAL".equals(request.getRequestStatus())
    			|| !"PENDING".equals(request.getApprovalStatus())) {
    		throw new ResponseStatusException(
    				HttpStatus.CONFLICT,
    				"결재 대기 중인 요청이 아닙니다.");
    	}
    	
    	if(actorId.equals(request.getDrafterId())) {
    		throw new ResponseStatusException(
    				HttpStatus.FORBIDDEN,
    				"요청자는 본인 요청을 반려할 수 없습니다");
    	}
    	
    	PendingApproval pending = 
    			approvalMapper.selectPendingApprovalForUpdate(requestId, actorId);
    	
    	if(pending == null) {
    		throw new ResponseStatusException(
    				HttpStatus.FORBIDDEN,
    				"현재 결재 권한이 없습니다.");
    	}
    	
    	boolean departmentApproval = 
    			Integer.valueOf(1).equals(pending.getApprovalOrder())
    				&& "DIRECTOR".equals(pending.getApprovalType());
    	boolean finalApproval = 
    			Integer.valueOf(2).equals(pending.getApprovalOrder())
    				&& "INTERMEDIATE".equals(pending.getApprovalType());
    	
    	if(!departmentApproval && !finalApproval) {
    		throw new ResponseStatusException(
    				HttpStatus.CONFLICT,
    				"지원하지 않는 결재 단계입니다.");
    	}
    	
    	if(approvalMapper.rejectPendingApproval(
    			pending.getApprovalId(),
    			actorId,
    			commentText.strip()) != 1) {
    		throw new ResponseStatusException(
    				HttpStatus.CONFLICT,
    				"결재 상태가 변경되었습니다. 다시 확인하세요.");
    	}
    	
    	// 다음 결재 단계가 WAITING일 때 취소합니다.
    	approvalMapper.cancelWaitingApprovals(requestId);
    	
    	if(approvalMapper.markRequestRejected(requestId) != 1) {
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
                        .filter(candidate -> !candidate.getUserId().equals(actorId))
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
                        .filter(candidate -> !candidate.getUserId().equals(actorId))
                        .toList();

        if (departmentApprovers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "활성 상태의 소속 부서장이 없어 제출할 수 없습니다.");
        }

        if (departmentApprovers.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "소속 부서장이 여러 명이라 결재자를 확정할 수 없습니다.");
        }

        Long departmentApproverId =
                departmentApprovers.get(0).getUserId();

        if (dbApproverId == null
                || dbApproverId.equals(actorId)
                || dbApproverId.equals(departmentApproverId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "요청자 및 부서장과 다른 DB팀 결재자를 선택하세요.");
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
                    "선택한 사용자는 현재 활성 DB_APPROVER 결재자가 아닙니다.");
        }

        // 1단계 부서장, 2단계 선택 DB팀 결재자 순서로 결재선을 생성합니다.
        approvalMapper.insertApproval(
                requestId,
                departmentApproverId,
                1,
                "DIRECTOR",
                "PENDING");
        approvalMapper.insertApproval(
                requestId,
                dbApproverId,
                2,
                "INTERMEDIATE",
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