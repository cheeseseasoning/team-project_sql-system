package com.sqlflow.execution.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import com.sqlflow.config.MySqlValidationDbConfig;

class MySqlValidationConnectionConfigTest {
    @Test void missingConfigStartsWithoutValidationPool() {
        try (var context = context(false)) {
            assertFalse(context.containsBean("validationDataSource"));
            assertNotNull(context.getBean(SqlExplainValidationService.class));
        }
    }
    @Test void configuredPoolIsSeparateFromReader() {
        try (var context = context(true)) {
            var pool = (com.zaxxer.hikari.HikariDataSource) context.getBean("validationDataSource");
            assertNotSame(context.getBean("queryDataSource"), pool);
            assertEquals("jdbc:mysql://localhost:3306/validation_test", pool.getJdbcUrl());
            assertEquals("sql_validator", pool.getUsername());
        }
    }
    @org.springframework.context.annotation.Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties
    static class BindingConfig { }

    private AnnotationConfigApplicationContext context(boolean configured) {
        var context = new AnnotationConfigApplicationContext();
        context.register(BindingConfig.class);
        if (configured) context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                "app.datasource.validation.url", "jdbc:mysql://localhost:3306/validation_test",
                "app.datasource.validation.username", "sql_validator",
                "app.datasource.validation.password", "unused-test-password")));
        context.registerBean("queryDataSource", DataSource.class, () -> mock(DataSource.class));
        context.register(MySqlValidationDbConfig.class, SqlBasicValidator.class, SqlExplainValidationService.class);
        context.refresh();
        return context;
    }
}
