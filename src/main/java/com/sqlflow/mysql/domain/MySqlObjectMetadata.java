package com.sqlflow.mysql.domain;

import lombok.Getter;
import lombok.Setter;

/** MySQL 데이터 사전에서 확인한 실제 테이블·뷰 정보. */
@Getter
@Setter
public class MySqlObjectMetadata {

    private String databaseName;
    private String objectName;
    private String objectType;
    private String objectComment;
}
