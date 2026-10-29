package com.sqlflow.request.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * 저장 전 작성 화면의 SQL 목록.
 * 제목·목적 입력 여부와 관계없이 SQL만 검사한다.
 */
@Getter
@Setter
public class ViewCreateSqlValidationRequestDTO {

    @NotEmpty
    @Size(max = 5)
    private List<@NotNull @Size(max = 20000) String> sqls;
}