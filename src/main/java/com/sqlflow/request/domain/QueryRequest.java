package com.sqlflow.request.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryRequest {

    private Long requestId;
    private String requestNo;

    private Long drafterId;
    private Long deptId;

    private String title;
    private String purpose;
}