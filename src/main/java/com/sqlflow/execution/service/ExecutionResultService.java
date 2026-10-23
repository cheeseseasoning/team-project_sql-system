package com.sqlflow.execution.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.domain.ExecutionResultFile;
import com.sqlflow.execution.dto.ExecutionResultDetailResponse;
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
    
    @Transactional(
    	    transactionManager = "managementTransactionManager",
    	    readOnly = true
    	)
    	public ExecutionResultDetailResponse getMyResultDetail(
    	        Long executionId,
    	        Long drafterId) {

    	    if (drafterId == null || drafterId <= 0) {
    	        throw new ResponseStatusException(
    	                HttpStatus.UNAUTHORIZED,
    	                "로그인이 필요합니다.");
    	    }

    	    if (executionId == null || executionId <= 0) {
    	        throw new ResponseStatusException(
    	                HttpStatus.BAD_REQUEST,
    	                "올바른 실행 ID를 입력하세요.");
    	    }

    	    ExecutionResultDetailResponse detail =
    	            executionResultMapper.selectMyResultDetail(
    	                    executionId,
    	                    drafterId);

    	    if (detail == null) {
    	        throw new ResponseStatusException(
    	                HttpStatus.NOT_FOUND,
    	                "실행 정보를 찾을 수 없습니다.");
    	    }

    	    return detail;
    	}
    
    /**
     * 본인 실행 결과인지 확인하고 이용 가능한 파일 정보를 반환한다.
     * 실제 파일 읽기는 호출하는 쪽에서 수행한다.
     */
    @Transactional(
            transactionManager = "managementTransactionManager",
            readOnly = true
    )
    public ExecutionResultFile getMyResultFile(
            Long executionId,
            Long drafterId) {

        if (drafterId == null || drafterId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }

        if (executionId == null || executionId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "올바른 실행 ID를 입력하세요.");
        }

        ExecutionResultFile result =
                executionResultMapper.selectMyResultFile(
                        executionId,
                        drafterId);

        // 다른 사용자의 실행도 조회 결과가 없으므로 동일하게 처리한다.
        if (result == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "실행 정보를 찾을 수 없습니다.");
        }

        if (!"SUCCESS".equals(result.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "성공한 실행의 결과만 조회할 수 있습니다.");
        }

        if (result.getResultId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "저장된 결과 정보가 없습니다.");
        }

        if (result.getDeletedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "결과 파일이 삭제되었습니다.");
        }

        LocalDateTime now =
                LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        if (result.getExpiresAt() != null
                && !result.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "결과 파일의 보관 기간이 만료되었습니다.");
        }

        if (result.getFilePath() == null
                || result.getFilePath().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "결과 파일 경로가 누락되었습니다.");
        }

        return result;
    }
}