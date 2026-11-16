package com.sqlflow.config;

import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** DML EXPLAIN 전용 연결. 조회 실행용 연결이나 MyBatis에 연결하지 않는다. */
@Configuration
@ConditionalOnProperty(name = "app.datasource.validation.url")
public class MySqlValidationDbConfig {
    @Bean
    @ConfigurationProperties("app.datasource.validation")
    public DataSourceProperties validationDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.validation.hikari")
    public DataSource validationDataSource(
            @Qualifier("validationDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }
}
