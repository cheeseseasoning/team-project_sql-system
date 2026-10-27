package com.sqlflow.mysql.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.mysql.domain.MySqlColumnMetadata;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;

/**
 * 업무 MySQL 연결에서 테이블·뷰·컬럼 구조만 조회한다.
 * 직원별 접근 정책은 Oracle 관리 DB에서 별도로 확인한다.
 */
@Mapper
public interface MySqlMetadataMapper {

    String selectCurrentDatabase();

    /** 이후 객체 이름 해석에 사용할 서버의 대소문자 처리 설정. */
    int selectLowerCaseTableNames();

    /** 정확한 DB명·객체명에 해당하는 테이블 또는 뷰가 있는지 확인한다. */
    int countSelectableObjects(
            @Param("databaseName") String databaseName,
            @Param("objectName") String objectName);

    List<MySqlColumnMetadata> selectColumns(
            @Param("databaseName") String databaseName,
            @Param("objectName") String objectName);
    
    /**
     * 서버의 대소문자 처리 설정을 적용해 대상을 찾고 실제 이름을 반환한다.
     * 대상이 없거나 현재 DB 계정에 보이지 않으면 null을 반환한다.
     */
    MySqlObjectMetadata selectObject(
            @Param("databaseName") String databaseName,
            @Param("objectName") String objectName);

    List<MySqlObjectMetadata> selectSelectableObjects();
}
