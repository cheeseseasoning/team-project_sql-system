package com.sqlflow.request.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RequestItemResponse {

    private Long queryId;
    private Integer queryOrder;
    private String queryTitle;

    private String sqlText;
    private String authoringType;
    private String validationStatus;
    private String itemClass;
}