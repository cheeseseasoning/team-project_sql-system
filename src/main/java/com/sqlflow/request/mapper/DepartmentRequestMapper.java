package com.sqlflow.request.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.sqlflow.request.dto.DepartmentRequestListResponse;

@Mapper
public interface DepartmentRequestMapper {

    long countVisibleRequests(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("status") String status);

    List<DepartmentRequestListResponse> selectVisibleRequestsPage(
            @Param("actorId") Long actorId,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("size") int size);
}