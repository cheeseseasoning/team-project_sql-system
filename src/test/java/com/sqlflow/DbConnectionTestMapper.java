package com.sqlflow;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DbConnectionTestMapper {

    @Select("SELECT 1 FROM DUAL")
    int selectOne();

    @Select("SELECT USER FROM DUAL")
    String selectUser();
}