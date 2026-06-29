package com.gameplatform.central.infrastructure.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration class that enables Spring's scheduled task execution capability
 * globally within the central-system application.
 * <p>
 * By decorating this class with {@link EnableScheduling}, the Spring container
 * will automatically detect any bean methods annotated with {@code @Scheduled}
 * (such as in {@code UserReplicationSchedulerService}) and register them for
 * asynchronous background execution.
 * This class must be filled if the system needs multi Spring threads.
 * (One Spring thread is completely able to handle multi connections simultaneously)
 * </p>
 * <p>
 * Isolating this marker configuration in a dedicated class adheres to the Single
 * Responsibility Principle (SRP). It decouples scheduling activation from the
 * main application bootstrapper, allowing it to be easily tuned (e.g. by
 * configuring a custom {@code ThreadPoolTaskScheduler} bean) or disabled/mocked
 * in integration testing.
 * </p>
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

