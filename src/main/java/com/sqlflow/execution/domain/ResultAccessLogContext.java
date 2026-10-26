package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 접근 로그 기록에만 사용하는 서버 내부 식별정보.
 * 파일 제공 권한을 의미하지 않으며 API 응답으로 반환하지 않는다.
 */
@Getter
@Setter
public class ResultAccessLogContext {

    private Long requestId;
    private Long resultId;
    private Long userId;
}