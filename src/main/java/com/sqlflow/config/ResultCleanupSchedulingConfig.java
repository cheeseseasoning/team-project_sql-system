package com.sqlflow.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.sqlflow.execution.scheduler.ExpiredResultCleanupScheduler;
import com.sqlflow.execution.service.ExpiredResultCleanupBatchService;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
        name = "sqlflow.result.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class ResultCleanupSchedulingConfig {

    @Bean
    public ExpiredResultCleanupScheduler expiredResultCleanupScheduler(
            ExpiredResultCleanupBatchService batchService,
            @Value("${sqlflow.result.cleanup.batch-size:100}") int batchSize,
            @Value("${sqlflow.result.cleanup.run-on-startup:false}")
            boolean runOnStartup) {

        return new ExpiredResultCleanupScheduler(
                batchService,
                batchSize,
                runOnStartup);
    }
}