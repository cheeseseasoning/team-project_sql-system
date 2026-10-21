package com.sqlflow.access.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * 실제 조회 대상에 적용되는 부서 정책.
 * 조회 대상의 이름은 Oracle 메타데이터에서 확인한 이름을 사용한다.
 */
@Getter
@Setter
public class DataAccessPolicy {

    private Long resourceId;
    private Long policyId;

    private String owner;
    private String objectName;
    private String accessLevel;
}