package com.sqlflow.request.service;

public interface ApprovalLineCreator {

    void createApprovalLine(
            Long requestId,
            Long actorId,
            Long dbApproverId);
}