package com.sqlflow.mysql.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 업무 DB에 실제 존재하는 뷰의 기본 정보.
 * SQLFlow 등록 이름과 정책 정보는 이후 Service에서 조합한다.
 */
@Getter
@Setter
public class MySqlViewSummaryDTO {

    private String schemaName;
    private String objectName;
}