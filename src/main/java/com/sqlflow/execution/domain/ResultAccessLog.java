package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 실행 결과 접근 기록.
 * 접근 시각은 Oracle에서 기록한다.
 */
@Getter
@Setter
public class ResultAccessLog {

    private Long logId;
    private Long requestId;
    private Long resultId;
    private Long userId;
    private String actionType;
    private String accessResult;
}