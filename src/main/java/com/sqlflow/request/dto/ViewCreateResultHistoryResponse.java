package com.sqlflow.request.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ViewCreateResultHistoryResponse {
    private String previousResultStatus;
    private String previousResultContent;
    private String previousRegisteredByName;
    private LocalDateTime previousRegisteredAt;
    private String changedByName;
    private LocalDateTime changedAt;
    private String changeReason;
}
