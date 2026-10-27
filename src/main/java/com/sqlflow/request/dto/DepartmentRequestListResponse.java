package com.sqlflow.request.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DepartmentRequestListResponse extends RequestListResponse {

    private String drafterName;
    private String deptName;
    private LocalDateTime submittedAt;
}