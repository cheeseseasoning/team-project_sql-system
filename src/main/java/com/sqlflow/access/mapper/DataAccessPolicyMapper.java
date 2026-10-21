package com.sqlflow.access.mapper;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.access.domain.DataAccessPolicy;
import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.execution.domain.OracleObjectName;

/**
 * 관리 DB에서 요청자의 현재 상태와 부서별 데이터 정책을 조회한다.
 * 업무 DB에는 연결하지 않는다.
 */
@Mapper
public interface DataAccessPolicyMapper {

    UserAccount selectRequester(@Param("userId") Long userId);

    List<DataAccessPolicy> selectPolicies(
            @Param("deptId") Long deptId,
            @Param("targets") Set<OracleObjectName> targets);
}