package com.sqlflow.request.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 공통 작성 화면에서 보낸 데이터 변경 초안 입력을 받는다. */
public record DmlDraftRequest(
        @NotBlank @Size(max = 200)
        String title,

        @NotBlank
        String purpose,

        String detailContent,

        @NotBlank @Pattern(regexp = "Y|N")
        String isUrgent,

        LocalDate desiredDate,

        @NotEmpty @Size(max = 5)
        List<@NotNull @Valid SqlEntry> items
) {
    /** SQL 항목은 배열 순서대로 저장한다. */
    public record SqlEntry(
            @Size(max = 200)
            String queryTitle,

            @NotBlank @Size(max = 20000)
            String sqlText
    ) {
    }
}