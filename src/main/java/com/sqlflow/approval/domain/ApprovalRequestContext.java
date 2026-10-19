package com.sqlflow.approval.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovalRequestContext {

    private Long requestId;
    private Long drafterId;
    private Long deptId;
    private String requestStatus;
}
