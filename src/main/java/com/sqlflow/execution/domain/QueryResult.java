package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 실행 결과 파일의 메타데이터.
 * 실제 데이터는 파일에 저장하고 DB에는 저장소 기준 상대 경로를 기록한다.
 */
@Getter
@Setter
public class QueryResult {

    private Long resultId;
    private Long executionId;
    private String filePath;
    private Long rowCount;
    private Long fileSize;
}