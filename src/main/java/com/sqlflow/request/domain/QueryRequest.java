package com.sqlflow.request.domain;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryRequest {

    private Long requestId;
    private String requestNo;
    private String requestType;
    
    private Long sourceRequestId;

    private Long drafterId;
    private Long deptId;

    private String title;
    private String purpose;
    
    private String detailContent;
    private String isUrgent;
    private LocalDate desiredDate;
}
