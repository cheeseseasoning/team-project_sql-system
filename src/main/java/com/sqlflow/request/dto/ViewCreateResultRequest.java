package com.sqlflow.request.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ViewCreateResultRequest {

    @NotBlank
    private String resultStatus;

    @NotBlank
    @Size(max = 4000)
    private String resultContent;

    @Size(max = 1000)
    private String changeReason;
}
