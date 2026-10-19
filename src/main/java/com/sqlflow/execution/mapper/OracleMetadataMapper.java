package com.sqlflow.execution.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.execution.domain.OracleColumnMetadata;
import com.sqlflow.execution.domain.OracleSynonymMetadata;

/**
 * Oracle 데이터 사전에서 객체와 컬럼 정보를 조회한다.
 * 조회 결과는 접속한 DB 계정에서 확인할 수 있는 범위다.
 */
@Mapper
public interface OracleMetadataMapper {

    /** 스키마가 생략된 이름을 해석할 때 사용할 현재 스키마를 조회한다. */
    String selectCurrentSchema();

    /** 지정한 스키마에 해당 테이블·뷰가 있는지 확인한다. */
    int countSelectableObjects(@Param("owner") String owner, @Param("objectName") String objectName);

    /** 지정한 객체의 컬럼 이름과 자료형을 조회한다. */
    List<OracleColumnMetadata> selectColumns(@Param("owner") String owner, @Param("objectName") String objectName);
    
    /** 지정한 스키마의 동의어가 가리키는 대상 정보를 조회한다. */
    OracleSynonymMetadata selectSynonym(
            @Param("owner") String owner,
            @Param("synonymName") String synonymName);
}