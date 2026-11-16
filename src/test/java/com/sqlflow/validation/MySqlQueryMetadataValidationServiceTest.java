package com.sqlflow.validation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import com.sqlflow.execution.service.*;
import com.sqlflow.execution.domain.QueryObjectName;
import com.sqlflow.mysql.service.MySqlTableValidationService;
import com.sqlflow.mysql.mapper.MySqlMetadataMapper;
import com.sqlflow.mysql.domain.MySqlObjectMetadata;

class MySqlQueryMetadataValidationServiceTest {
    private MySqlMetadataMapper mapper;
    private SqlExplainValidationService explain;
    private MySqlQueryMetadataValidationService service;

    @BeforeEach void setUp() {
        mapper = mock(MySqlMetadataMapper.class);
        explain = mock(SqlExplainValidationService.class);
        service = new MySqlQueryMetadataValidationService(new SqlParserValidator(),
                new MySqlTableValidationService(new SqlTableExtractor(), mapper), new SqlBasicValidator(), explain);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT * FROM users u CROSS JOIN department d;",
        "SELECT DISTINCT id FROM users LIMIT 10;",
        "SELECT id FROM users UNION SELECT id FROM department;",
        "WITH c AS (SELECT id FROM users) SELECT * FROM c;",
        "SELECT * FROM (SELECT id FROM users) c;",
        "SELECT id FROM users WHERE EXISTS (SELECT id FROM department);",
        "SELECT id, COUNT(*) FROM users GROUP BY id HAVING COUNT(*) > 1;"
    })
    void relaxesExpressionRulesButKeepsAllResourceChecks(String sql) {
        when(explain.validate("SELECT", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.VALID, "OK"));
        when(mapper.selectCurrentDatabase()).thenReturn("demo");
        when(mapper.selectObject(anyString(), anyString())).thenAnswer(call -> {
            var metadata = new MySqlObjectMetadata();
            metadata.setDatabaseName(call.getArgument(0));
            metadata.setObjectName(call.getArgument(1));
            return metadata;
        });
        var result = service.validate(sql);
        assertTrue(result.resources().contains(new QueryObjectName("demo", "users")));
        var expected = new java.util.HashSet<QueryObjectName>();
        expected.add(new QueryObjectName("demo", "users"));
        if (sql.contains("department")) expected.add(new QueryObjectName("demo", "department"));
        assertEquals(expected, result.resources());
        verify(mapper, never()).selectColumns(anyString(), anyString());
    }

    @Test void explainFailureStopsBeforePolicyMetadata() {
        String sql = "SELECT missing FROM users;";
        when(explain.validate("SELECT", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.OBJECT_ERROR, "missing"));
        assertThrows(ResponseStatusException.class, () -> service.validate(sql));
        verifyNoInteractions(mapper);
    }

    @Test void unknownTargetsCannotBypassSelectPolicy() {
        String sql = "SELECT 1;";
        when(explain.validate("SELECT", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.VALID, "OK"));
        when(mapper.selectCurrentDatabase()).thenReturn("demo");
        assertThrows(ResponseStatusException.class, () -> service.validate(sql));
    }
    @Test void incompleteExplainBlocksSelect() {
        String sql = "SELECT * FROM users;";
        when(explain.validate("SELECT", sql)).thenReturn(new SqlExplainValidationService.Result(SqlExplainValidationService.State.INCOMPLETE, "offline"));
        var error = assertThrows(ResponseStatusException.class, () -> service.validate(sql));
        assertEquals(503, error.getStatusCode().value());
        verifyNoInteractions(mapper);
    }

}
