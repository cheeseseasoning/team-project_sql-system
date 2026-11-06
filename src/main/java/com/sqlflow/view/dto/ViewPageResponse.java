package com.sqlflow.view.dto;

import java.util.List;

/** 뷰 목록의 현재 페이지와 검색 조건에 맞는 전체 건수를 반환한다. */
public record ViewPageResponse(
        List<ViewSummaryResponseDTO> items,
        int page,
        int size,
        long totalCount,
        long totalPages) {
}