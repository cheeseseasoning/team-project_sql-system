package com.sqlflow.ai.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.ai.dto.SqlGenerationRequest.SqlType;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.request.service.CreateTableSqlValidationService;
import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.ViewCreateSqlValidationService;

import lombok.RequiredArgsConstructor;

/** Checks generated SQL before returning it to the browser; it never executes SQL. */
@Service
@RequiredArgsConstructor
public class AiGeneratedSqlValidationService {

    private final MySqlQueryMetadataValidationService queryMetadataValidationService;
    private final DataAccessValidationService accessValidationService;
    private final ViewCreateSqlValidationService viewCreateSqlValidationService;
    private final DmlSqlValidationService dmlSqlValidationService;

    /**
     * @return a Korean clarification message if generated SQL fails validation, otherwise null
     */
    public String validate(SqlType type, String sql, Long userId) {
        try {
            switch (type) {
                case SELECT -> {
                    var metadata = queryMetadataValidationService.validate(sql);
                    accessValidationService.validate(userId, metadata.resources());
                }
                case CREATE_VIEW -> {
                    var result = viewCreateSqlValidationService.validate(sql, userId);
                    if (result.getStatus() != com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status.VALID) {
                        return "생성된 뷰 SQL을 검증하지 못했습니다. " + result.getMessage()
                                + " 요청을 구체화한 뒤 다시 입력해 주세요.";
                    }
                }
                case CREATE_TABLE -> {
                    var result = CreateTableSqlValidationService.validate(sql);
                    if (result.getStatus() != com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status.VALID) {
                        return "생성된 테이블 SQL을 확인하지 못했습니다. " + result.getMessage()
                                + " 요청을 구체화한 뒤 다시 입력해 주세요.";
                    }
                }
                case INSERT, UPDATE, DELETE -> {
                    var result = dmlSqlValidationService.validate(
                            DmlSqlValidationService.SqlType.valueOf(type.name()), sql);
                    if (result.status() != DmlSqlValidationService.Status.VALID) {
                        return "생성된 SQL의 기본 구문을 확인하지 못했습니다. " + result.message()
                                + " 요청을 구체화한 뒤 다시 입력해 주세요.";
                    }
                }
            }
            return null;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw exception;
            }
            String reason = exception.getReason();
            return "생성된 SQL의 대상 또는 권한을 확인하지 못했습니다. "
                    + (reason == null ? "허용된 테이블·컬럼만 대상으로 요청해 주세요." : reason);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "생성된 SQL을 현재 DB 메타데이터로 검증할 수 없습니다.",
                    exception);
        }
    }
}
