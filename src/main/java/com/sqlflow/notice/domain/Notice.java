package com.sqlflow.notice.domain;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공지 상세 조회 결과.
 */
@Getter
@NoArgsConstructor
public class Notice {

    private Long noticeId;
    private String title;
    private String content;

    private Long createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    private Long updatedBy;
    private String updatedByName;
    private LocalDateTime updatedAt;
}