package com.sqlflow.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MyPasswordMapper {

    String selectPasswordHashForUpdate(
            @Param("userId") Long userId);

    int updatePasswordHash(
            @Param("userId") Long userId,
            @Param("passwordHash") String passwordHash);
}