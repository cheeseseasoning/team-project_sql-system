package com.sqlflow.request.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ViewCreateResultResponse {
    private String resultStatus;
    private String resultContent;
    private String registeredByName;
    private LocalDateTime registeredAt;
}
