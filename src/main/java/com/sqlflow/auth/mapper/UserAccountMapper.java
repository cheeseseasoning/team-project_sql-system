package com.sqlflow.auth.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.auth.domain.UserAccount;
import com.sqlflow.auth.dto.AccountListItemResponse;

@Mapper
public interface UserAccountMapper {

    UserAccount selectByLoginId(@Param("loginId") String loginId);
    
    UserAccount selectAccountDetailById(@Param("userId") Long userId);

    List<String> selectRoleCodes(@Param("userId") Long userId);

    List<Map<String, Object>> selectDepartments();

    List<Map<String, Object>> selectPositions();
    
    List<AccountListItemResponse> selectAllAccountListItems();

    int countByLoginId(@Param("loginId") String loginId);
    
    int countByUserId(@Param("userId") Long userId);

    int countDepartment(@Param("deptId") Long deptId);

    int countPosition(@Param("positionId") Long positionId);

    Long nextUserId();

    List<Long> selectRoleIdsByCodes(@Param("roleCodes") List<String> roleCodes);
    
    int updateAccountInfo(
    		@Param("userId") Long userId,
    		@Param("userName") String userName,
    		@Param("deptId") Long deptId,
    		@Param("positionId") Long positionId,
    		@Param("enabled") String enabled);
    
    int deleteUserRoles(@Param("userId") Long userId);
    
    int updatePasswordHash(
    		@Param("userId") Long userId,
    		@Param("passwordHash") String passwordHash);

    int insertUser(
            @Param("userId") Long userId,
            @Param("deptId") Long deptId,
            @Param("positionId") Long positionId,
            @Param("loginId") String loginId,
            @Param("passwordHash") String passwordHash,
            @Param("userName") String userName);

    int insertUserRole(
            @Param("userId") Long userId,
            @Param("roleId") Long roleId);
}
