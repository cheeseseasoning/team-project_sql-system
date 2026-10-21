package com.sqlflow.mysql.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MySqlConnectionMapper {

    String selectDatabase();

    String selectCurrentUser();

    String selectMessage(@Param("id") int id);
}