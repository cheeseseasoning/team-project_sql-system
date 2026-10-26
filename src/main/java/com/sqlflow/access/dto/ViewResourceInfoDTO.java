package com.sqlflow.access.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Oracle에 등록된 리소스의 이름·설명·활성 상태.
 * 실제 뷰 여부는 MySQL 조회 결과와 대조한다.
 */
@Getter
@Setter
public class ViewResourceInfoDTO {

    private Long resourceId;
    private String schemaName;
    private String objectName;
    private String resourceName;
    private String description;
    private String enabled;
}