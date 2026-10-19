package com.sqlflow.request.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestListResponse {

    private Long requestId;
    private String requestNo;

    private String title;
    private String requestClass;
    private String requestStatus;
    private String approvalStatus;

    private Integer sqlCount;
    private LocalDateTime createdAt;
}