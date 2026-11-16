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
    private String requestType;

    private Long sourceRequestId;
    private String sourceRequestNo;

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
    private List<RequestApprovalResponse> approvals = new ArrayList<>();

    private String detailContent;
    private String isUrgent;
    private LocalDate desiredDate;
    private boolean canManageChangeSqlResult;

    // 작성자 상세 화면의 재기안 버튼 표시 여부.
    private boolean canRedraft;

    // 원본 SQL 목록 중 새 초안에 가져올 항목의 ID. 원본 순서를 유지한다.
    private List<Long> redraftQueryIds = new ArrayList<>();
}