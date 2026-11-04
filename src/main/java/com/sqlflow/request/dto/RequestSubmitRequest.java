package com.sqlflow.request.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestSubmitRequest {

    @NotEmpty(message = "결재자를 1명 이상 지정하세요.")
    private List<
            @NotNull(message = "결재자를 확인하세요.")
            @Positive(message = "올바른 결재자를 선택하세요.")
            Long> approverIds = new ArrayList<>();
}