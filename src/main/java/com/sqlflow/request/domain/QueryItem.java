package com.sqlflow.request.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueryItem {

    private Long queryId;
    private Long requestId;

    private Integer queryOrder;
    private String queryTitle;

    private String sqlText;
    private String authoringType;
}