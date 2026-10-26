package com.sqlflow.view.dto;

/**
 * 뷰 목록 화면에 제공할 정보.
 * registered는 Oracle 등록 여부이며, 활성 여부와는 별개다.
 */
public record ViewSummaryResponseDTO(
        String schemaName,
        String objectName,
        Long resourceId,
        String resourceName,
        String description,
        boolean registered,
        String enabled
) {
}