package com.sqlflow.request.service;

import java.util.List;

public interface ApprovalLineCreator {

    void createApprovalLine(
            Long requestId,
            Long actorId,
            List<Long> intermediateApproverIds,
            Long dbApproverId);
}