package com.sqlflow.execution.service;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.auth.security.SqlFlowUserDetails;
import com.sqlflow.execution.dto.ExecutionQueueDetailResponse;
import com.sqlflow.execution.dto.ExecutionQueuePageResponse;
import com.sqlflow.execution.mapper.ExecutionQueueMapper;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class ExecutionQueueService {
    private final ExecutionQueueMapper mapper;

    @Transactional(transactionManager="managementTransactionManager", readOnly=true)
    public ExecutionQueuePageResponse getQueue(SqlFlowUserDetails user, int page, int size, String keyword) {
        requireApprover(user);
        if (page < 1 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "페이지 값이 올바르지 않습니다.");
        String normalized = keyword == null ? "" : keyword.trim();
        long count = mapper.countReadyRequests(normalized);
        int pages = (int) ((count + size - 1) / size);
        return new ExecutionQueuePageResponse(mapper.selectReadyRequests(normalized, (page - 1) * size, size), page, size, count, pages);
    }

    @Transactional(transactionManager="managementTransactionManager", readOnly=true)
    public ExecutionQueueDetailResponse getDetail(SqlFlowUserDetails user, Long requestId) {
        requireApprover(user);
        if (requestId == null || requestId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "요청 ID가 올바르지 않습니다.");
        ExecutionQueueDetailResponse detail = mapper.selectRequestDetail(requestId);
        if (detail == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "승인된 요청을 찾을 수 없습니다.");
        detail.setSqlItems(mapper.selectRequestSqlItems(requestId));
        return detail;
    }

    private void requireApprover(SqlFlowUserDetails user) {
        if (user == null || user.getUserId() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        if (!user.isEnabled() || !mapper.isActiveDbApprover(user.getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "DB팀 실행 권한이 없습니다.");
    }
}
