package com.sqlflow.execution.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

/**
 * 미리보기와 다운로드에 사용할 실행 결과 파일 정보.
 * 파일 경로는 서버 내부에서만 사용한다.
 */
@Getter
@Setter
public class ExecutionResultFile {

    private Long executionId;
    private Long queryId;
    private Long resultId;
    private String status;
    private String filePath;
    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;
}