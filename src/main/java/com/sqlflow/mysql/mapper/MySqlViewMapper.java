package com.sqlflow.mysql.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.mysql.dto.MySqlViewColumnDTO;
import com.sqlflow.mysql.dto.MySqlViewDefinitionDTO;
import com.sqlflow.mysql.dto.MySqlViewSummaryDTO;

@Mapper
public interface MySqlViewMapper {

    /**
     * 현재 연결된 업무 DB의 실제 뷰 목록을 조회한다.
     * SQLFlow에 등록되지 않은 뷰도 포함한다.
     */
    List<MySqlViewSummaryDTO> selectViews();

    /**
     * 현재 업무 DB에 실제 존재하는 뷰인지 확인한다.
     * 원본 테이블과 다른 DB의 객체는 조회 대상으로 인정하지 않는다.
     */
    int countView(
            @Param("schemaName") String schemaName,
            @Param("objectName") String objectName);

    /** 실제 뷰의 컬럼을 정의된 순서대로 조회한다. */
    List<MySqlViewColumnDTO> selectViewColumns(
            @Param("schemaName") String schemaName,
            @Param("objectName") String objectName);
    
    /**
     * 현재 뷰의 CREATE문을 읽는다.
     * Service에서 현재 업무 DB의 실제 뷰인지 확인한 뒤 호출한다.
     */
    MySqlViewDefinitionDTO selectViewDefinition(
            @Param("schemaName") String schemaName,
            @Param("objectName") String objectName);
}