package com.sqlflow.request.dto;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestDraftUpdateRequest {

    @NotBlank(message = "요청 제목을 입력하세요.")
    @Size(max = 200, message = "요청 제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "조회 목적을 입력하세요.")
    private String purpose;
    
    private String detailContent;

    @NotBlank(message = "긴급 여부를 입력하세요.")
    @Pattern(
            regexp = "Y|N",
            message = "올바른 긴급 여부를 입력하세요."
    )
    private String isUrgent = "N";

    private LocalDate desiredDate;

    @NotNull(message = "SQL 목록이 필요합니다.")
    @Size(max = 5, message = "SQL은 최대 5개까지 등록할 수 있습니다.")
    private List<@NotBlank(message = "빈 SQL은 등록할 수 없습니다.") String> sqls;
    
    @Size(max = 5, message = "SQL 제목은 최대 5개까지 등록할 수 있습니다.")
    private List<
            @Size(max = 200, message = "SQL 제목은 200자 이하여야 합니다.")
            String> sqlTitles;

    @AssertTrue(message = "SQL 목록과 SQL 제목 목록의 개수가 일치해야 합니다.")
    public boolean isSqlTitleCountValid() {
        return sqlTitles == null
                || (sqls != null && sqlTitles.size() == sqls.size());
    }
    
}