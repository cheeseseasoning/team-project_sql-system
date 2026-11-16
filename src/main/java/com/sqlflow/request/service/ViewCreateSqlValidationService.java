package com.sqlflow.request.service;

import org.springframework.stereotype.Service;

import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO;
import com.sqlflow.request.dto.ViewCreateSqlValidationResultDTO.Status;

import lombok.RequiredArgsConstructor;

/**
 * 모든 CREATE의 기본 형식만 검사한다.
 * 상세 문법, 권한과 실제 실행 가능 여부는 DB팀이 검토한다.
 */
@Service
@RequiredArgsConstructor
public class ViewCreateSqlValidationService {

    private final SqlBasicValidator basicValidator;

    public ViewCreateSqlValidationResultDTO validate(String sqlText) {
        var result = basicValidator.validate("CREATE", sqlText);

        if (result.blocked()) {
            return new ViewCreateSqlValidationResultDTO(Status.BLOCKED, result.warning());
        }

        return new ViewCreateSqlValidationResultDTO(
                result.warning() == null ? Status.VALID : Status.WARNING,
                result.warning() == null
                        ? "CREATE 기본 형식을 확인했습니다. 문법·권한·실행 가능 여부는 DB팀 검토가 필요합니다."
                        : result.warning());
    }

    /**
     * 기존 AI·요청 서비스의 호출 형식을 유지한다.
     * CREATE는 요청자와 관계없이 동일한 기본 형식 검사만 수행한다.
     */
    public ViewCreateSqlValidationResultDTO validate(String sqlText, Long requesterId) {
        return validate(sqlText);
    }
}