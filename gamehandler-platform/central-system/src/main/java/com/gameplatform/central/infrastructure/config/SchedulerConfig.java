package com.gameplatform.central.infrastructure.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Configuration class that enables Spring's scheduled task execution capability
 * globally within the central-system application.
 * <p>
 * By decorating this class with {@link EnableScheduling}, the Spring container
 * will automatically detect any bean methods annotated with {@code @Scheduled}
 * (such as in {@code UserReplicationSchedulerService}) and register them for
 * asynchronous background execution.
 * </p>
 * <p>
 * Isolating this marker configuration in a dedicated class adheres to the Single
 * Responsibility Principle (SRP). It decouples scheduling activation from the
 * main application bootstrapper, allowing it to be easily tuned (e.g. by
 * configuring a custom {@code ThreadPoolTaskScheduler} bean) or disabled/mocked
 * in integration testing.
 * </p>
 * <p>
 * C-R4: declares two thread-pool beans:
 * <ul>
 *   <li>{@code taskScheduler} — a {@link ThreadPoolTaskScheduler} with a
 *       configurable pool size (default {@code 4}). Spring resolves the
 *       {@code TaskScheduler} backing {@code @Scheduled} by looking up a bean
 *       named {@code taskScheduler}; declaring it here replaces the implicit
 *       single-thread {@code ConcurrentTaskScheduler} fallback, so multiple
 *       {@code @Scheduled} methods (e.g. the upcoming
 *       {@code LocalServerHealthMonitorService}) can run concurrently.</li>
 *   <li>{@code replicationPushExecutor} — a dedicated
 *       {@link ThreadPoolTaskExecutor} used by
 *       {@code UserReplicationSchedulerService} to push user-replication events
 *       to every active local server in parallel.</li>
 * </ul>
 * </p>
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Multi-threaded {@link ThreadPoolTaskScheduler} backing all
     * {@code @Scheduled} methods. The bean is intentionally named
     * {@code taskScheduler} because that is the conventional name Spring's
     * {@code @Scheduled} infrastructure resolves when looking for a
     * {@code TaskScheduler}; without it Spring falls back to a single-thread
     * {@code ConcurrentTaskScheduler}, which serialises every scheduled task.
     *
     * @param poolSize number of scheduler threads (defaults to 4)
     */
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${app.scheduler.pool-size:4}") int poolSize) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("central-sched-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        return scheduler;
    }

    /**
     * Dedicated executor for the per-server parallel push performed by
     * {@code UserReplicationSchedulerService}. Kept separate from
     * {@link #taskScheduler(int)} so that blocking I/O on local servers never
     * starves {@code @Scheduled} methods (and vice-versa).
     *
     * @param parallelism core/max pool size (defaults to 4)
     */
    @Bean("replicationPushExecutor")
    public ThreadPoolTaskExecutor replicationPushExecutor(
            @Value("${app.replication.push-parallelism:4}") int parallelism) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(parallelism);
        executor.setMaxPoolSize(parallelism);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("repl-push-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}

