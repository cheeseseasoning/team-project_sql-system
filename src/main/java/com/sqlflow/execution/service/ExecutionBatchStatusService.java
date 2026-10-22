package com.sqlflow.execution.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionBatchStatusResponseDTO;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;

import lombok.RequiredArgsConstructor;

/** 현재 사용자가 열람할 수 있는 실행 묶음의 상태를 조회한다. */
@Service
@RequiredArgsConstructor
public class ExecutionBatchStatusService {

    private final ExecutionBatchMapper batchMapper;

    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true)
    public ExecutionBatchStatusResponseDTO getStatus(
            Long batchId, SqlFlowUserDetails user) {

        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        if (batchId == null || batchId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "실행 묶음 ID가 올바르지 않습니다.");
        }

        var result = batchMapper.selectVisibleBatchStatus(
                batchId, user.getUserId());

        if (result == null) {
            // 권한 없는 사용자에게 다른 요청의 존재 여부를 알려주지 않는다.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "조회할 실행 묶음을 찾을 수 없습니다.");
        }

        return result;
    }
}