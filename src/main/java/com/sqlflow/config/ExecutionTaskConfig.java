package com.sqlflow.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 업무 DB의 부하를 고려해 실행 묶음의 동시 처리 수를 제한한다. */
@Configuration
public class ExecutionTaskConfig {

    @Bean(name = "sqlExecutionExecutor")
    public ThreadPoolTaskExecutor sqlExecutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 애플리케이션 한 개에서 실행 묶음 하나씩 처리한다.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);

        // 메모리에 작업을 쌓지 않고, 처리 중이면 새 접수를 거절한다.
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("sql-execution-");

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);

        return executor;
    }
}