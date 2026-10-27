package com.sqlflow.execution.domain;

import lombok.Getter;
import lombok.Setter;

/** 만료 정리 대상의 식별자와 저장소 상대 경로. */
@Getter
@Setter
public class ExpiredResultFile {

    private Long resultId;
    private String filePath;
}