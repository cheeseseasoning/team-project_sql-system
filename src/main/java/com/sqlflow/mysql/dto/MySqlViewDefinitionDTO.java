package com.sqlflow.mysql.dto;

import lombok.Getter;
import lombok.Setter;

/** MySQL에서 읽은 현재 뷰 이름과 CREATE문. */
@Getter
@Setter
public class MySqlViewDefinitionDTO {

    private String objectName;
    private String createStatement;
}