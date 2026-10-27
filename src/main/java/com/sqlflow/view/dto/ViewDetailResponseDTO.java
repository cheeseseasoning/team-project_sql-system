package com.sqlflow.view.dto;

import java.util.List;

import com.sqlflow.access.dto.ViewDepartmentPolicyInfoDTO;
import com.sqlflow.mysql.dto.MySqlViewColumnDTO;

/**
 * 뷰 상세 화면에 필요한 정보를 모아 반환한다.
 * 등록 정보와 정책은 Oracle, 컬럼과 CREATE문은 MySQL에서 읽는다.
 */
public record ViewDetailResponseDTO(
        String schemaName,
        String objectName,
        Long resourceId,
        String resourceName,
        String description,
        boolean registered,
        String enabled,
        List<ViewDepartmentPolicyInfoDTO> departmentPolicies,
        List<MySqlViewColumnDTO> columns,
        String createStatement
) {
}