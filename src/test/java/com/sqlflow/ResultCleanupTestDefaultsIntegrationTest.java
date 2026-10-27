package com.sqlflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

import com.sqlflow.config.ResultCleanupSchedulingConfig;
import com.sqlflow.execution.scheduler.ExpiredResultCleanupScheduler;

/**
 * true 설정이 있어도 공통 테스트 설정이 자동 정리를 끄는지 확인한다.
 * 실제 DB 연결이나 파일 삭제는 하지 않는다.
 */
@SpringBootTest(
        classes = ResultCleanupTestDefaultsIntegrationTest.TestConfig.class,
        properties = "sqlflow.result.cleanup.enabled=true")
class ResultCleanupTestDefaultsIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private ApplicationContext context;

    @Test
    void disablesAutomaticCleanupForSpringTests() {
        assertEquals(
                Boolean.FALSE,
                environment.getProperty(
                        "sqlflow.result.cleanup.enabled",
                        Boolean.class));

        assertTrue(context.getBeansOfType(
                ExpiredResultCleanupScheduler.class).isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    @Import(ResultCleanupSchedulingConfig.class)
    static class TestConfig {
    }
}