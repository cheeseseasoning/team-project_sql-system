package com.sqlflow.request.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestSubmitRequest {

    // 개인·부서가 섞인 결재선. 목록 순서가 결재 순서가 된다.
    private List<@NotNull @Valid ApprovalTargetRequest> approvalTargets = new ArrayList<>();

    // 기존 개인 결재 제출과 테스트의 호출 형식은 유지한다.
    private List<
            @NotNull(message = "결재자를 확인하세요.")
            @Positive(message = "올바른 결재자를 선택하세요.")
            Long> approverIds = new ArrayList<>();

    // 두 입력 형식 중 하나만 사용하고, 결재 대상은 최소 한 개여야 한다.
    @AssertTrue(message = "결재선이 올바르지 않습니다. 결재 대상을 1개 이상 다시 지정해 주세요.")
    public boolean isApprovalLineValid() {
        boolean hasTargets = approvalTargets != null && !approvalTargets.isEmpty();
        boolean hasApproverIds = approverIds != null && !approverIds.isEmpty();

        return hasTargets != hasApproverIds;
    }

    /** 기존 개인 지정도 공통 형식으로 변환해 서비스의 처리 경로를 통일한다. */
    public List<ApprovalTargetRequest> resolveApprovalTargets() {
        if (approvalTargets != null && !approvalTargets.isEmpty()) {
            return approvalTargets;
        }

        if (approverIds == null || approverIds.isEmpty()) {
            return List.of();
        }

        return approverIds.stream()
                .map(ApprovalTargetRequest::forUser)
                .toList();
    }
}