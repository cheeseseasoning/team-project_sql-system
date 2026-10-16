package com.sqlflow.request.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDraftCreateRequest {

    @NotBlank(message = "요청 제목을 입력하세요.")
    @Size(max = 200, message = "요청 제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "조회 목적을 입력하세요.")
    private String purpose;

    @NotNull(message = "SQL 목록이 필요합니다.")
    @Size(max = 5, message = "SQL은 최대 5개까지 등록할 수 있습니다.")
    private List<@NotBlank(message = "빈 SQL은 등록할 수 없습니다.") String> sqls;
}