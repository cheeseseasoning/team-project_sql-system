package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Oracle 동의어가 가리키는 대상 정보.
 * DB 링크가 있으면 원격 객체이므로 현재 검증 범위에서는 차단한다.
 */
@Getter
@Setter
public class OracleSynonymMetadata {

    private String tableOwner;
    private String tableName;
    private String dbLink;
}