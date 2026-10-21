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
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@MapperScan(
    basePackages = {
        "com.sqlflow.auth.mapper",
        "com.sqlflow.request.mapper",
        "com.sqlflow.approval.mapper",
        "com.sqlflow.execution.mapper"
    },
    sqlSessionFactoryRef = "managementSqlSessionFactory"
)
public class OracleManagementDbConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties managementDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = {"managementDataSource", "dataSource"})
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource managementDataSource(
            @Qualifier("managementDataSourceProperties")
            DataSourceProperties properties) {

        return properties.initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }

    @Bean(name = {"managementSqlSessionFactory", "sqlSessionFactory"})
    @Primary
    public SqlSessionFactory managementSqlSessionFactory(
            @Qualifier("managementDataSource")
            DataSource dataSource) throws Exception {

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);

        var resolver = new PathMatchingResourcePatternResolver();

        // 기존 Oracle Mapper XML만 등록
        var locations = new java.util.ArrayList<
                org.springframework.core.io.Resource>();

        for (String path : new String[] {
                "classpath*:/mapper/auth/**/*.xml",
                "classpath*:/mapper/request/**/*.xml",
                "classpath*:/mapper/approval/**/*.xml",
                "classpath*:/mapper/execution/**/*.xml",
                "classpath*:/mapper/metadata/**/*.xml"
        }) {
            java.util.Collections.addAll(
                    locations,
                    resolver.getResources(path)
            );
        }

        factory.setMapperLocations(
                locations.toArray(
                        new org.springframework.core.io.Resource[0]
                )
        );

        var configuration =
                new org.apache.ibatis.session.Configuration();

        configuration.setMapUnderscoreToCamelCase(true);
        factory.setConfiguration(configuration);

        return factory.getObject();
    }

    @Bean(name = {
        "managementTransactionManager",
        "transactionManager"
    })
    @Primary
    public PlatformTransactionManager managementTransactionManager(
            @Qualifier("managementDataSource")
            DataSource dataSource) {

        return new DataSourceTransactionManager(dataSource);
    }
}