package com.sqlflow.notice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지 등록 입력. 생성 후 값을 변경할 수 없다. */
public record NoticeCreateRequest(

        @NotBlank(message = "공지 제목을 입력하세요.")
        @Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
        String title,

        @NotBlank(message = "공지 본문을 입력하세요.")
        String content
) {
}