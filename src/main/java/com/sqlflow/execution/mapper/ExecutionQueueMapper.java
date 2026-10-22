package com.sqlflow.execution.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.sqlflow.execution.dto.ExecutionQueueDetailResponse;
import com.sqlflow.execution.dto.ExecutionQueueItemResponse;
import com.sqlflow.execution.dto.ExecutionQueueSqlResponse;

@Mapper
public interface ExecutionQueueMapper {
    boolean isActiveDbApprover(@Param("userId") Long userId);
    long countReadyRequests(@Param("keyword") String keyword);
    List<ExecutionQueueItemResponse> selectReadyRequests(@Param("keyword") String keyword,
            @Param("offset") int offset, @Param("size") int size);
    ExecutionQueueDetailResponse selectRequestDetail(@Param("requestId") Long requestId);
    List<ExecutionQueueSqlResponse> selectRequestSqlItems(@Param("requestId") Long requestId);
}
