package com.sqlflow.execution.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.ExecutionStatus;
import com.sqlflow.execution.domain.ExecutionBatch;
import com.sqlflow.execution.domain.ExecutionRequestContext;
import com.sqlflow.execution.domain.QueryExecution;
import com.sqlflow.execution.mapper.ExecutionBatchMapper;
import com.sqlflow.execution.mapper.QueryExecutionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 최종 승인된 요청의 실행 묶음과 SQL별 대기 이력을 등록한다.
 * DB_APPROVER 권한이 있으면 해당 요청의 승인자가 아니어도 등록할 수 있다.
 */
@Service
@RequiredArgsConstructor
public class ExecutionBatchService {

    private static final String EXECUTION_AUTHORITY = "ROLE_DB_APPROVER";
    private static final String FINAL_APPROVAL_STATUS = "APPROVED";
    private static final String READY_REQUEST_STATUS = "READY";

    private static final String MANUAL_TRIGGER = "MANUAL";
    private static final String INITIAL_BATCH = "INITIAL";

    private final ExecutionBatchMapper executionBatchMapper;
    private final QueryExecutionMapper queryExecutionMapper;

    /**
     * 최초 수동 실행을 위한 대기 이력을 등록하고 실행 묶음 ID를 반환한다.
     * user는 Controller가 서버의 로그인 정보에서 가져와 전달한다.
     */
    @Transactional
    public Long createInitialBatch(Long requestId, SqlFlowUserDetails user) {

        validateExecutor(user);

        if (requestId == null || requestId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 ID가 올바르지 않습니다.");
        }

        // 같은 요청의 실행 묶음이 동시에 등록되지 않도록 요청 행을 잠근다.
        ExecutionRequestContext request = executionBatchMapper.selectRequestContextForUpdate(requestId);

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "조회 요청을 찾을 수 없습니다.");
        }

        if (!FINAL_APPROVAL_STATUS.equals(request.getApprovalStatus())
                || !READY_REQUEST_STATUS.equals(request.getRequestStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "최종 승인된 실행 대기 요청만 실행 등록할 수 있습니다.");
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

        // 실행자 ID는 사용자가 입력한 값이 아닌 서버 로그인 정보에서 가져온다.
        batch.setInitiatedBy(user.getUserId());
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

    /** 로그인 상태와 SQL 실행에 필요한 권한을 확인한다. */
    private void validateExecutor(SqlFlowUserDetails user) {

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        boolean hasExecutionAuthority = user.getAuthorities().stream()
                .anyMatch(authority -> EXECUTION_AUTHORITY.equals(authority.getAuthority()));

        if (!user.isEnabled() || !hasExecutionAuthority) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SQL 실행 권한이 없습니다.");
        }
    }
}