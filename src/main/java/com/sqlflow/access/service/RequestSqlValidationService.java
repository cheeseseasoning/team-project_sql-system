package com.sqlflow.access.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;

import lombok.RequiredArgsConstructor;

/**
 * 제출할 SQL 전체에 구조·메타데이터 검사와 접근 정책 검사를 적용한다.
 * SQL은 요청 본문이 아니라 관리 DB에 저장된 원문을 전달받는다.
 */
@Service
@RequiredArgsConstructor
public class RequestSqlValidationService {

    private final MySqlQueryMetadataValidationService metadataValidationService;
    private final DataAccessValidationService dataAccessValidationService;

    /**
     * SQL 중 하나라도 검증에 실패하면 예외를 전달하여 제출을 차단한다.
     * 실제 SQL 실행이나 결재선 생성은 하지 않는다.
     */
    public void validate(Long requesterId, List<String> sqlTexts) {

        if (sqlTexts == null || sqlTexts.isEmpty() || sqlTexts.size() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "제출하려면 SQL을 1개 이상, 5개 이하로 등록하세요.");
        }

        for (String sqlText : sqlTexts) {
            var metadata = metadataValidationService.validate(sqlText);

            dataAccessValidationService.validate(
                    requesterId, metadata.resources());
        }
    }
}