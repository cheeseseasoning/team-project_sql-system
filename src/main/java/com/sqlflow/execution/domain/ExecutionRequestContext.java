package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 실행 가능 여부를 판단하기 위한 요청 상태.
 * 전체 요청 상태와 최종 결재 상태를 함께 확인한다.
 */
@Getter
@Setter
public class ExecutionRequestContext {

    private Long requestId;
    private String requestStatus;
    private String approvalStatus;
}