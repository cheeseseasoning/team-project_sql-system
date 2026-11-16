package com.sqlflow.request.dto;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestItemResponse {

    private Long queryId;
    private Integer queryOrder;
    private String queryTitle;

    private String sqlText;
    private String authoringType;
    private String validationStatus;
    private String itemClass;

    // SELECT의 최신 실행 상태. 실행 이력이 없으면 null이다.
    private String latestExecutionStatus;

    // 외부 결과 등록 화면에서 사용하며, 저장 시에는 서버가 원문으로 다시 판별한다.
    private String viewLinkType;
    private String createdViewSchema;
    private String createdViewName;

    // 외부 처리 결과를 아직 등록하지 않은 SQL은 아래 값이 null이다.
    private String changeResultStatus;
    private String changeResultNote;
    private Long changeExecutedBy;
    private Long changeRegisteredBy;
    private LocalDateTime changeRegisteredAt;

    // 표시용 이름만 추가하며, 결과 저장 시 사용하는 사용자 ID는 유지한다.
    private String changeExecutedByName;
    private String changeExecutedDeptName;
    private String changeRegisteredByName;
    private String changeRegisteredDeptName;

    private java.util.List<ChangeResultHistoryItem> changeResultHistory;

    @Getter
    @Setter
    public static class ChangeResultHistoryItem {

        private Long historyId;
        private String previousResultStatus;
        private String previousResultNote;
        private String changeType;
        private String changeReason;
        private String changedByName;
        private LocalDateTime changedAt;
    }
}