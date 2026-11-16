package com.sqlflow.request.service;

import org.springframework.stereotype.Service;
import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;
import lombok.RequiredArgsConstructor;

/** 기존 API·저장 유형은 유지하고 모든 CREATE의 기본 형식만 검사한다. */
@Service
@RequiredArgsConstructor
public class ViewCreateSqlValidationService {
    private final SqlBasicValidator basicValidator;

    public ViewCreateSqlValidationResultDTO validate(String sqlText) {
        var result = basicValidator.validate("CREATE", sqlText);
        if (result.blocked()) return new ViewCreateSqlValidationResultDTO(Status.BLOCKED, result.warning());
        return new ViewCreateSqlValidationResultDTO(result.warning() == null ? Status.VALID : Status.WARNING,
                result.warning() == null ? "CREATE 기본 형식을 확인했습니다. 문법·권한·실행 가능 여부는 DB팀 검토가 필요합니다." : result.warning());
    }
}
