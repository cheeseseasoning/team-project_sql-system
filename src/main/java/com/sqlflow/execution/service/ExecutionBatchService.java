package com.sqlflow.execution.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.ExecutionStatus;
import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 승인된 요청의 최초 실행 묶음을 등록한다.
 * SQL 실행과 실패 SQL 재실행은 별도 기능으로 처리한다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionBatchService {

    // 2번 담당자의 결재 기능과 공유할 최종 승인 상태값
    private static final String FINAL_APPROVAL_STATUS = "APPROVED";

    // 실행 방식과 최초 실행 구분값. 팀과 합의할 때 이곳에서 변경한다.
    private static final String MANUAL_TRIGGER = "MANUAL";
    private static final String INITIAL_BATCH = "INITIAL";

    private final ExecutionBatchMapper executionBatchMapper;
    private final QueryExecutionMapper queryExecutionMapper;

    /**
     * 최초 수동 실행을 위한 대기 묶음을 등록하고 발급된 ID를 반환한다.
     * initiatedBy는 추후 인증 정보에서 가져온 DB팀 실행자의 사용자 ID여야 한다.
     */
    @Transactional
    public Long createInitialBatch(Long requestId, Long initiatedBy) {

        if (requestId == null || requestId <= 0 || initiatedBy == null || initiatedBy <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 ID와 실행자 ID가 올바르지 않습니다.");
        }

        String approvalStatus = executionBatchMapper.selectApprovalStatusForUpdate(requestId);

        if (approvalStatus == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
        }

        if (!FINAL_APPROVAL_STATUS.equals(approvalStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "DB팀 최종 승인이 완료된 요청만 실행할 수 있습니다.");
        }

        List<Long> queryIds = executionBatchMapper.selectQueryIdsByRequestId(requestId);

        if (queryIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "실행할 SQL이 없습니다.");
        }

        // 최초 실행의 중복 등록을 막는다. 재실행은 별도 절차를 사용한다.
        if (executionBatchMapper.countBatchesByRequestId(requestId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 실행 묶음이 등록된 요청입니다.");
        }

        ExecutionBatch batch = new ExecutionBatch();
        batch.setRequestId(requestId);
        batch.setInitiatedBy(initiatedBy);
        batch.setTriggerType(MANUAL_TRIGGER);
        batch.setBatchType(INITIAL_BATCH);
        batch.setBatchStatus(ExecutionStatus.PENDING.name());

        if (executionBatchMapper.insertBatch(batch) != 1) {
            throw new IllegalStateException("실행 묶음 등록에 실패했습니다.");
        }
        
        // 실행 묶음에 속한 SQL마다 최초 실행 대기 이력을 생성한다.
        for (Long queryId : queryIds) {
            QueryExecution execution = new QueryExecution();
            execution.setBatchId(batch.getBatchId());
            execution.setQueryId(queryId);
            execution.setAttemptNo(1);
            execution.setRetryOfExecutionId(null);

            if (queryExecutionMapper.insertExecution(execution) != 1) {
                throw new IllegalStateException("SQL 실행 이력 등록에 실패했습니다. queryId=" + queryId);
            }
        }

        return batch.getBatchId();
    }
}