package com.sqlflow.request.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestSubmitRequest {

    @NotNull(message = "DB팀 실행 승인자를 선택하세요.")
    @Positive(message = "올바른 DB팀 실행 승인자를 선택하세요.")
    private Long dbApproverId;
    
    private List<@NotNull @Positive Long> intermediateApproverIds = 
    		new ArrayList<>();
}