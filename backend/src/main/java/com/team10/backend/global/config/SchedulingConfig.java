package com.team10.backend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@Slf4j
public class SchedulingConfig {

    /**
     * 커스텀 스케줄링 스레드 풀 생성
     */
    @Bean
    public TaskScheduler taskScheduler(
            @Value("${app.scheduling.pool-size:6}") int poolSize,
            @Value("${app.scheduling.await-termination-seconds:10}") int awaitTerminationSeconds
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(awaitTerminationSeconds);
        scheduler.setErrorHandler(
                ex -> log.error(
                        "Scheduled task failed",
                        ex
                )
        );
        scheduler.initialize();
        return scheduler;
    }
}
