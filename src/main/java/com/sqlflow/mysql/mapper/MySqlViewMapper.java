package com.sqlflow.mysql.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.sqlflow.mysql.dto.MySqlViewSummaryDTO;

@Mapper
public interface MySqlViewMapper {

    /**
     * 현재 연결된 업무 DB의 실제 뷰 목록을 조회한다.
     * SQLFlow에 등록되지 않은 뷰도 포함한다.
     */
    List<MySqlViewSummaryDTO> selectViews();
}