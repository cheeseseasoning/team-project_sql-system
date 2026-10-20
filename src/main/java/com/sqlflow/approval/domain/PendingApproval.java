package com.sqlflow.approval.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PendingApproval {

    private Long approvalId;
    private Integer approvalOrder;
    private String approvalType;
}
