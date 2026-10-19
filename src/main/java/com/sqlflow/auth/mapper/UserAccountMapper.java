package com.sqlflow.auth.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.auth.domain.UserAccount;

@Mapper
public interface UserAccountMapper {

    UserAccount selectByLoginId(@Param("loginId") String loginId);

    List<String> selectRoleCodes(@Param("userId") Long userId);
}
