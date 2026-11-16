package com.sqlflow.request.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableDraftRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    @NotBlank
    private String purpose;

    private String detailContent;

    @NotBlank
    @Pattern(regexp = "Y|N")
    private String isUrgent;

    private LocalDate desiredDate;

    @NotEmpty
    @Size(max = 5)
    private List<@NotNull @Valid SqlEntry> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SqlEntry {

        @NotBlank(message = "SQL 제목을 입력하세요.")
        @Size(max = 200)
        private String queryTitle;

        @NotBlank
        @Size(max = 20000)
        private String sqlText;
    }
}
