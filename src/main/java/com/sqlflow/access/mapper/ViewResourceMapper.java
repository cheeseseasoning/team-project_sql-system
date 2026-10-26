package com.sqlflow.access.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.access.dto.ViewDepartmentInfoDTO;
import com.sqlflow.access.dto.ViewDepartmentPolicyInfoDTO;
import com.sqlflow.access.dto.ViewResourceInfoDTO;

@Mapper
public interface ViewResourceMapper {

    /**
     * 지정 스키마의 등록 정보를 조회한다.
     * 비활성 등록도 포함해 미등록과 구분한다.
     */
    List<ViewResourceInfoDTO> selectResources(
            @Param("schemaName") String schemaName);
    
    /** 드롭다운에 사용할 전체 부서를 조회한다. */
    List<ViewDepartmentInfoDTO> selectDepartments();

    /**
     * 지정 리소스의 전체 부서 정책을 조회한다.
     * 정책 미등록·비활성 상태도 상세 화면에서 구분한다.
     */
    List<ViewDepartmentPolicyInfoDTO> selectDepartmentPolicies(
            @Param("resourceId") Long resourceId);
    
    /**
     * 선택 부서에 활성 정책이 등록된 활성 리소스를 조회한다.
     * Service에서 실제 MySQL 뷰 목록과 대조해 목록을 필터링한다.
     */
    List<ViewResourceInfoDTO> selectResourcesByDepartment(
            @Param("schemaName") String schemaName,
            @Param("deptId") Long deptId);
}