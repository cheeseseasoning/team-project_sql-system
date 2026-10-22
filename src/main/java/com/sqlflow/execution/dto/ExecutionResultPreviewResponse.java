package com.sqlflow.execution.dto;

import java.util.List;

/**
 * CSV 결과의 일부를 화면에 전달한다.
 * offset은 헤더를 제외한 데이터 행 기준이며 0부터 시작한다.
 */
public record ExecutionResultPreviewResponse(
        List<String> columns,
        List<List<String>> rows,
        long nextOffset,
        boolean hasMore) {
}