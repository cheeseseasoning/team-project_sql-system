package com.sqlflow.approval.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalActionContext {

    private Long requestId;
    private Long drafterId;
    private String requestStatus;
    private String approvalStatus;
}
