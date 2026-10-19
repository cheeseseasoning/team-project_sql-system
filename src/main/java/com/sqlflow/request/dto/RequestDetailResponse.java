package com.sqlflow.request.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDetailResponse {

    private Long requestId;
    private String requestNo;

    private String title;
    private String purpose;

    private String drafterName;
    private String deptName;

    private String requestClass;
    private String requestStatus;
    private String approvalStatus;

    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;

    private List<RequestItemResponse> items = new ArrayList<>();
    
    private String detailContent;
    private String priority;
    private LocalDate desiredDate;
}