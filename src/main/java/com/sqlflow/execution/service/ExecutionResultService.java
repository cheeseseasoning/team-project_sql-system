package com.sqlflow.execution.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.dto.ExecutionResultListResponse;
import com.sqlflow.execution.dto.ExecutionResultPageResponse;
import com.sqlflow.execution.mapper.ExecutionResultMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExecutionResultService {

    private final ExecutionResultMapper executionResultMapper;

    @Transactional(
        transactionManager = "managementTransactionManager",
        readOnly = true
    )
    public ExecutionResultPageResponse getMyResults(
            Long drafterId,
            int page,
            int size) {

        if (drafterId == null || drafterId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (page < 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 번호는 1 이상이어야 합니다.");
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "페이지 크기는 1~100 사이여야 합니다.");
        }

        long totalCount =
                executionResultMapper.countMyLatestResults(drafterId);

        long totalPages =
                totalCount / size + (totalCount % size == 0 ? 0 : 1);

        long offset = (long) (page - 1) * size;

        List<ExecutionResultListResponse> items =
                totalCount == 0 || offset >= totalCount
                        ? List.of()
                        : executionResultMapper.selectMyLatestResults(
                                drafterId,
                                offset,
                                size);

        return new ExecutionResultPageResponse(
                items,
                page,
                size,
                totalCount,
                totalPages);
    }
}