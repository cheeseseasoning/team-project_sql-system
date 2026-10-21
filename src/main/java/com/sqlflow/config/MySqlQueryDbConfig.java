package com.sqlflow.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@MapperScan(
    basePackages = "com.sqlflow.mysql.mapper",
    sqlSessionFactoryRef = "querySqlSessionFactory"
)
public class MySqlQueryDbConfig {

    @Bean
    @ConfigurationProperties("app.datasource.query")
    public DataSourceProperties queryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("app.datasource.query.hikari")
    public DataSource queryDataSource(
            @Qualifier("queryDataSourceProperties")
            DataSourceProperties properties) {

        return properties.initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    @Bean
    public SqlSessionFactory querySqlSessionFactory(
            @Qualifier("queryDataSource")
            DataSource dataSource) throws Exception {

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        factory.setMapperLocations(
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:/mapper/mysql/**/*.xml")
        );

        var configuration =
                new org.apache.ibatis.session.Configuration();

        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        return factory.getObject();
    }

    @Bean
    public PlatformTransactionManager queryTransactionManager(
            @Qualifier("queryDataSource")
            DataSource dataSource) {

        return new DataSourceTransactionManager(dataSource);
    }
}