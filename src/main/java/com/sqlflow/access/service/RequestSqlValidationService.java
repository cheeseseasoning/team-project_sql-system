package com.sqlflow.access.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.execution.service.SqlParserValidator;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.request.dto.RequestItemResponse;
import com.sqlflow.request.dto.RequestSqlValidationResult;

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
    private final SqlParserValidator sqlParserValidator;

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
            validateOne(requesterId, sqlText);
        }
    }

    /** Validate every saved item independently so the UI can show each result. */
    public List<RequestSqlValidationResult> validateItems(
            Long requesterId,
            List<RequestItemResponse> items) {

        if (items == null || items.isEmpty() || items.size() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "검증하려면 SQL을 1개 이상, 5개 이하로 등록하세요.");
        }

        List<RequestSqlValidationResult> results = new ArrayList<>();
        for (RequestItemResponse item : items) {
            try {
                var access = validateOne(requesterId, item.getSqlText());
                results.add(new RequestSqlValidationResult(
                        item.getQueryId(), "VALID", "검증을 통과했습니다.",
                        access.accessLevel(), access.resourceIds().stream().sorted().toList()));
            } catch (ResponseStatusException exception) {
                String message = Objects.requireNonNullElse(
                        exception.getReason(), "SQL 검증에 실패했습니다.");
                results.add(new RequestSqlValidationResult(
                        item.getQueryId(), "INVALID", message, null, List.of()));
            }
        }
        return results;
    }

    private DataAccessValidationService.ValidationResult validateOne(
            Long requesterId, String sqlText) {
        // Parse before entering the MySQL-backed metadata transaction so malformed
        // SQL can still receive a useful validation result while MySQL is unavailable.
        sqlParserValidator.parseSelect(sqlText);

        MySqlQueryMetadataValidationService.ValidationResult metadata;
        try {
            metadata = metadataValidationService.validate(sqlText);
        } catch (DataAccessException | TransactionException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SQL 검증에 필요한 MySQL 메타데이터 DB에 연결할 수 없습니다. "
                            + "DB 연결 상태를 확인한 뒤 다시 검증하세요.",
                    exception);
        }
        return dataAccessValidationService.validate(requesterId, metadata.resources());
    }
}
