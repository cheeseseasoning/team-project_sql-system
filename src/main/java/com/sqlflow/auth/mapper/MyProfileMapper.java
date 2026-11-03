package com.sqlflow.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.auth.dto.MyProfileResponse;
import com.sqlflow.auth.dto.MyProfileUpdateRequest;

@Mapper
public interface MyProfileMapper {

    MyProfileResponse selectProfileByUserId(
            @Param("userId") Long userId);
    
    int updateContact(
            @Param("userId") Long userId,
            @Param("input") MyProfileUpdateRequest input);
}