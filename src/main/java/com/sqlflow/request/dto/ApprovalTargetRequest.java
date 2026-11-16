package com.sqlflow.request.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalTargetRequest {

    @NotBlank(message = "결재 대상 유형을 선택하세요.")
    @Pattern(regexp = "USER|DEPARTMENT", message = "올바른 결재 대상 유형을 선택하세요.")
    private String targetType;

    @Positive(message = "올바른 결재자를 선택하세요.")
    private Long approverId;

    @Positive(message = "올바른 담당 부서를 선택하세요.")
    private Long approverDeptId;

    // 개인과 부서를 동시에 지정하거나, 둘 다 지정하지 않는 입력을 차단한다.
    @AssertTrue(message = "개인 지정은 결재자를, 부서 지정은 담당 부서를 선택하세요.")
    public boolean isTargetValid() {
        if ("USER".equals(targetType)) {
            return approverId != null && approverDeptId == null;
        }

        if ("DEPARTMENT".equals(targetType)) {
            return approverId == null && approverDeptId != null;
        }

        return false;
    }

    /** 기존 개인 ID 목록을 공통 결재 대상 형식으로 변환한다. */
    public static ApprovalTargetRequest forUser(Long approverId) {
        ApprovalTargetRequest target = new ApprovalTargetRequest();
        target.setTargetType("USER");
        target.setApproverId(approverId);
        return target;
    }
}