package com.sqlflow.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sqlflow.access.service.DataAccessValidationService;
import com.sqlflow.ai.dto.SqlGenerationRequest;
import com.sqlflow.ai.dto.SqlGenerationRequest.SqlType;
import com.sqlflow.execution.service.MySqlQueryMetadataValidationService;
import com.sqlflow.execution.service.SqlBasicValidator;
import com.sqlflow.request.service.DmlSqlValidationService;
import com.sqlflow.request.service.ViewCreateSqlValidationService;

import tools.jackson.databind.ObjectMapper;

/** 실제 외부 API 호출 없이 CREATE 지침 전송과 생성 후 검증을 함께 확인한다. */
class CreateSqlGenerationTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AiSchemaContextService schema = mock(AiSchemaContextService.class);
    private final MySqlQueryMetadataValidationService metadata = mock(MySqlQueryMetadataValidationService.class);
    private final DataAccessValidationService access = mock(DataAccessValidationService.class);
    private MockRestServiceServer server;
    private OpenAiSqlGenerationService service;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var validation = new AiGeneratedSqlValidationService(metadata, access,
                new ViewCreateSqlValidationService(new SqlBasicValidator()), mock(DmlSqlValidationService.class));
        service = new OpenAiSqlGenerationService(builder, json, schema, validation);
        ReflectionTestUtils.setField(service, "apiKey", "test-key-not-sent-to-network");
        ReflectionTestUtils.setField(service, "model", "test-model");
        when(schema.loadForUser(1L, 2L)).thenReturn(List.of());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "CREATE TABLE product (product_id BIGINT PRIMARY KEY, product_name VARCHAR(100));",
            "CREATE VIEW active_product AS SELECT product_id FROM product;",
            "CREATE INDEX idx_product_name ON product (product_name);"
    })
    void acceptsDifferentCreateObjectsThroughOneType(String sql) {
        expectResponse(sql, false);
        var result = service.generate(1L, 2L, new SqlGenerationRequest(SqlType.CREATE, "요청한 객체를 생성해 주세요."));

        assertEquals(sql, result.getSql());
        assertFalse(result.isNeedsClarification());
        verifyNoInteractions(metadata, access);
        server.verify();
    }

    @Test
    void returnsClarificationWithoutSql() {
        expectResponse("", true);
        var result = service.generate(1L, 2L, new SqlGenerationRequest(SqlType.CREATE, "객체 만들어줘"));

        assertTrue(result.isNeedsClarification());
        assertEquals("", result.getSql());
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE product;",
            "CREATE TABLE product (id INT)",
            "CREATE TABLE product (id INT); DELETE FROM product;"
    })
    void refusesWrongTypeOrInvalidBasicFormat(String sql) {
        expectResponse(sql, false);
        var result = service.generate(1L, 2L, new SqlGenerationRequest(SqlType.CREATE, "상품 테이블 생성"));

        assertTrue(result.isNeedsClarification());
        assertEquals("", result.getSql());
        assertTrue(result.getExplanation().contains("CREATE SQL"));
        server.verify();
    }

    private void expectResponse(String sql, boolean clarification) {
        String output = json.writeValueAsString(Map.of("sql", sql, "explanation", "요청을 확인해 주세요.",
                "needsClarification", clarification));
        String response = json.writeValueAsString(Map.of("status", "completed", "output", List.of(
                Map.of("content", List.of(Map.of("type", "output_text", "text", output))))));

        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var body = json.readTree(((MockClientHttpRequest) request).getBodyAsString());
                    String instructions = body.path("instructions").asText();
                    assertTrue(instructions.contains("exactly one MySQL CREATE statement"));
                    assertTrue(instructions.contains("TABLE, VIEW, or INDEX"));
                    assertTrue(instructions.contains("ask for clarification"));
                    assertTrue(instructions.contains("New object and column names"));
                    assertTrue(instructions.contains("Never execute SQL"));
                    assertFalse(body.path("store").asBoolean(true));
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
