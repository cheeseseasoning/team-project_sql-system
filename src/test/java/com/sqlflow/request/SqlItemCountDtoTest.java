package com.sqlflow.request;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.sqlflow.request.controller.RequestController.DmlSqlValidationRequest;
import com.sqlflow.request.dto.*;
import com.sqlflow.request.service.DmlSqlValidationService.SqlType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/** 실제 Bean Validation으로 목록 개수를 검사하며 DB에는 연결하지 않는다. */
class SqlItemCountDtoTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void close() {
        factory.close();
    }

    @ParameterizedTest(name = "{0}: SQL {1}개")
    @MethodSource("requests")
    void allowsTenAndRejectsEleven(String type, int count) {
        var violations = validator.validate(request(type, count));
        if (count == 10) {
            assertTrue(violations.isEmpty(), violations.toString());
        } else {
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().allMatch(v -> v.getConstraintDescriptor().getAnnotation() instanceof jakarta.validation.constraints.Size));
            assertTrue(violations.stream().allMatch(v -> java.util.Set.of("sqls", "sqlTitles", "items").contains(v.getPropertyPath().toString())));
        }
    }

    static Stream<Arguments> requests() {
        return Stream.of("SELECT_VALIDATE", "SELECT_CREATE", "SELECT_UPDATE", "CREATE_VALIDATE",
                "CREATE_DRAFT", "DML_DRAFT", "DML_VALIDATE")
                .flatMap(type -> IntStream.of(10, 11).mapToObj(count -> Arguments.of(type, count)));
    }

    private Object request(String type, int count) {
        List<String> sqls = IntStream.rangeClosed(1, count).mapToObj(i -> "SELECT id FROM t;").toList();
        List<String> titles = IntStream.rangeClosed(1, count).mapToObj(i -> "SQL " + i).toList();
        switch (type) {
            case "SELECT_VALIDATE": return new SelectSqlValidationRequest(sqls);
            case "SELECT_CREATE": {
                var dto = new RequestDraftCreateRequest();
                dto.setTitle("조회 개수 확인"); dto.setPurpose("경계값 검사");
                dto.setSqls(sqls); dto.setSqlTitles(titles);
                return dto;
            }
            case "SELECT_UPDATE": {
                var dto = new RequestDraftUpdateRequest();
                dto.setTitle("조회 수정 개수 확인"); dto.setPurpose("경계값 검사");
                dto.setSqls(sqls); dto.setSqlTitles(titles);
                return dto;
            }
            case "CREATE_VALIDATE": {
                var dto = new ViewCreateSqlValidationRequestDTO(); dto.setSqls(sqls); return dto;
            }
            case "CREATE_DRAFT": {
                var dto = new ViewCreateDraftRequest();
                dto.setTitle("CREATE 개수 확인"); dto.setPurpose("경계값 검사");
                dto.setItems(IntStream.rangeClosed(1, count).mapToObj(i -> {
                    var item = new ViewCreateDraftRequest.SqlEntry();
                    item.setQueryTitle("SQL " + i); item.setSqlText("CREATE TABLE t" + i + " (id INT);");
                    return item;
                }).toList());
                return dto;
            }
            case "DML_DRAFT": return new DmlDraftRequest(SqlType.INSERT, "변경 개수 확인", "경계값 검사", null, "N", null,
                    IntStream.rangeClosed(1, count).mapToObj(i -> new DmlDraftRequest.SqlEntry("SQL " + i, "INSERT INTO t VALUES (" + i + ");")).toList());
            case "DML_VALIDATE": {
                var dto = new DmlSqlValidationRequest(); dto.setSqlType(SqlType.INSERT); dto.setSqls(sqls); return dto;
            }
            default: throw new IllegalArgumentException(type);
        }
    }
}
