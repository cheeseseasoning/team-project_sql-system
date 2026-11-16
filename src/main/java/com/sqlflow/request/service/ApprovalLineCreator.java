package com.sqlflow.request.service;

import java.util.List;

import com.sqlflow.request.dto.ApprovalTargetRequest;

public interface ApprovalLineCreator {

    void createApprovalLine(
            Long requestId,
            Long actorId,
            List<Long> approverIds);

    void createApprovalLineWithTargets(
            Long requestId,
            Long actorId,
            List<ApprovalTargetRequest> targets);
}