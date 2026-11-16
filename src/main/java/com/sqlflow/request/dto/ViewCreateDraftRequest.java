package com.sqlflow.request.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ViewCreateDraftRequest {

    @NotBlank @Size(max = 200)
    private String title;

    @NotBlank
    private String purpose;

    private String detailContent;

    @NotBlank @Pattern(regexp = "Y|N")
    private String isUrgent = "N";

    private LocalDate desiredDate;

    @NotEmpty
    @Size(max = 10, message = "SQL은 최대 10개까지 등록할 수 있습니다.")
    private List<@NotNull @Valid SqlEntry> items;

    @Getter
    @Setter
    public static class SqlEntry {

        @NotBlank(message = "SQL 제목을 입력하세요.") @Size(max = 200)
        private String queryTitle;

        @NotBlank
        private String sqlText;
    }
}