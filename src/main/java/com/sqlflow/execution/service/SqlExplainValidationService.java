package com.sqlflow.execution.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** 원문 실행·ANALYZE 없이 일반 EXPLAIN 결과만 확인한다. */
@Service
public class SqlExplainValidationService {
    private final DataSource queryDataSource;
    private final DataSource validationDataSource;
    private final SqlBasicValidator basicValidator;

    public SqlExplainValidationService(@Qualifier("queryDataSource") DataSource queryDataSource,
            @Qualifier("validationDataSource") @org.jspecify.annotations.Nullable DataSource validationDataSource,
            SqlBasicValidator basicValidator) {
        this.queryDataSource = queryDataSource;
        this.validationDataSource = validationDataSource;
        this.basicValidator = basicValidator;
    }

    public Result validate(String type, String sql) {
        var basic = basicValidator.validate(type, sql);
        if (basic.blocked()) return new Result(State.SYNTAX_ERROR, basic.warning());
        if (!java.util.Set.of("SELECT", "INSERT", "UPDATE", "DELETE").contains(type)) {
            throw new IllegalArgumentException("EXPLAIN 검증 대상 유형이 아닙니다.");
        }
        boolean select = "SELECT".equals(type);
        DataSource source = select ? queryDataSource : validationDataSource;
        if (source == null) {
            return new Result(State.INCOMPLETE, "DML EXPLAIN 검증용 DB 연결이 설정되지 않았습니다. DB팀 확인이 필요합니다.");
        }
        try (Connection connection = source.getConnection()) {
            // DML 원문은 실행하지 않는다. 일반 EXPLAIN을 위해 연결만 읽기 전용에서 제외한다.
            connection.setReadOnly(select);
            try (Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(15);
                // SQL은 수정·자동 포매팅하지 않고 검증용 접두사만 붙인다.
                try (var result = statement.executeQuery("EXPLAIN " + sql)) {
                    while (result.next()) { /* 실행 계획만 소비하고 조회 결과는 생성하지 않는다. */ }
                }
            }
            return new Result(State.VALID, "EXPLAIN 검증을 통과했습니다.");
        } catch (SQLException exception) {
            return classify(exception);
        }
    }

    /** 메시지 문자열 대신 MySQL 오류 코드로 명확한 구문 오류만 구분한다. */
    Result classify(SQLException exception) {
        int code = exception.getErrorCode();
        if (code == 1064 || code == 1149) {
            return new Result(State.SYNTAX_ERROR, "SQL 문법이 올바르지 않습니다. 키워드, 쉼표, 괄호와 입력값을 확인하세요. (DB 오류 코드: " + code + ")");
        }
        // 컬럼·테이블·집계·별칭 오류 등. SELECT는 차단하고 변경 요청은 DB팀 검토한다.
        if (java.util.Set.of(1046, 1052, 1054, 1055, 1060, 1066, 1109, 1111, 1136, 1146,
                1221, 1222, 1241, 1242, 1247, 1248, 1250, 1251).contains(code)) {
            return new Result(State.OBJECT_ERROR, objectErrorMessage(code) + " (DB 오류 코드: " + code + ")");
        }
        String message = switch (code) {
            case 1044, 1045, 1142, 1143, 1227 -> "검증용 DB 계정의 접속 또는 SQL 검사 권한이 부족합니다. DB팀에 계정 권한 확인을 요청하세요.";
            case 1792 -> "DB 연결이 읽기 전용으로 설정되어 변경 SQL을 검사하지 못했습니다. DB팀에 검증용 연결 설정 확인을 요청하세요.";
            case 1205, 1213, 3024 -> "DB 응답 지연 또는 잠금으로 SQL 검사를 완료하지 못했습니다. 잠시 후 다시 검증하세요.";
            default -> "DB 연결 또는 해당 SQL의 검사 지원 여부를 확인하지 못했습니다. 잠시 후 다시 검증하고, 계속되면 DB팀에 문의하세요.";
        };
        return new Result(State.INCOMPLETE, message + " (DB 오류 코드: " + code + ")");
    }

    /** 오류 상태와 차단 정책은 유지하고, 사용자에게 원인과 확인할 항목을 안내한다. */
    private String objectErrorMessage(int code) {
        return switch (code) {
            case 1046 -> "검사할 데이터베이스가 지정되지 않았습니다. 스키마명을 포함해 작성하거나 DB팀에 연결 설정 확인을 요청하세요.";
            case 1052 -> "여러 테이블에 같은 이름의 컬럼이 있습니다. 테이블명 또는 별칭을 컬럼 앞에 붙이세요.";
            case 1054 -> "존재하지 않거나 현재 위치에서 참조할 수 없는 컬럼입니다. 컬럼명과 테이블 별칭을 확인하세요.";
            case 1055, 1111 -> "집계 함수 또는 GROUP BY 사용이 올바르지 않습니다. SELECT, GROUP BY, HAVING의 컬럼과 함수를 확인하세요.";
            case 1060, 1066 -> "컬럼명 또는 테이블 별칭이 중복됩니다. 중복된 이름을 변경하세요.";
            case 1109, 1146 -> "참조한 테이블 또는 뷰를 찾을 수 없습니다. 스키마명과 객체명을 확인하세요.";
            case 1136 -> "INSERT 대상 컬럼 수와 입력값 수가 다릅니다. 컬럼과 값의 개수를 맞추세요.";
            case 1221 -> "SQL 구문의 조합이 올바르지 않습니다. 각 절의 위치와 함께 사용할 수 있는 구문인지 확인하세요.";
            case 1222 -> "UNION으로 연결한 SELECT의 컬럼 수가 다릅니다. 각 SELECT의 컬럼 수를 맞추세요.";
            case 1241, 1242 -> "서브쿼리의 컬럼 수 또는 결과 행 수가 사용 위치와 맞지 않습니다. 서브쿼리와 비교 조건을 확인하세요.";
            case 1247, 1248, 1250 -> "서브쿼리 또는 별칭 참조가 올바르지 않습니다. 별칭의 선언과 사용할 수 있는 위치를 확인하세요.";
            case 1251 -> "DB 인증 방식을 지원하지 않아 SQL을 검사하지 못했습니다. DB팀에 계정과 드라이버 확인을 요청하세요.";
            default -> "참조한 객체 또는 SQL 구조를 확인할 수 없습니다. 테이블, 컬럼과 별칭을 확인하세요.";
        };
    }

    public enum State { VALID, SYNTAX_ERROR, OBJECT_ERROR, INCOMPLETE }
    public record Result(State state, String message) { }
}
