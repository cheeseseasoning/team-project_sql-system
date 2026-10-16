package com.sqlflow.request.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.sqlflow.request.domain.QueryItem;
import com.sqlflow.request.domain.QueryRequest;

@Mapper
public interface RequestMapper {

    Long selectNextRequestId();

    int insertRequest(QueryRequest request);

    int insertItem(QueryItem item);
}