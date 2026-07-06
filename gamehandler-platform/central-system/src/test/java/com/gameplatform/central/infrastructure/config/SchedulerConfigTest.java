package com.gameplatform.central.infrastructure.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight unit test for {@link SchedulerConfig} that boots ONLY the config class
 * in a plain {@link AnnotationConfigApplicationContext} (no Spring Boot, no H2, no
 * web server). This keeps it fast and isolated while still exercising real bean
 * wiring and {@code @Value} resolution against the {@link org.springframework.core.env.Environment}.
 *
 * <p>C-R4: verifies
 * <ul>
 *   <li>the {@code taskScheduler} bean is a {@link ThreadPoolTaskScheduler} and that
 *       its {@code poolSize} follows {@code app.scheduler.pool-size} (default 4);</li>
 *   <li>the {@code replicationPushExecutor} bean is a {@link ThreadPoolTaskExecutor}
 *       whose core/max pool size follow {@code app.replication.push-parallelism}
 *       (default 4).</li>
 * </ul>
 * </p>
 */
class SchedulerConfigTest {

    private AnnotationConfigApplicationContext context;

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
            context = null;
        }
    }

    @Test
    void taskScheduler_isThreadPoolTaskSchedulerWithDefaultPoolSize4() {
        context = new AnnotationConfigApplicationContext();
        context.register(SchedulerConfig.class);
        context.refresh();

        Object taskSchedulerBean = context.getBean("taskScheduler");
        assertThat(taskSchedulerBean).isInstanceOf(ThreadPoolTaskScheduler.class);
        // poolSize is a private field on ThreadPoolTaskScheduler; read via reflection.
        Object poolSize = ReflectionTestUtils.getField(taskSchedulerBean, "poolSize");
        assertThat(poolSize).isEqualTo(4);
    }

    @Test
    void replicationPushExecutor_isThreadPoolTaskExecutorWithDefaultParallelism4() {
        context = new AnnotationConfigApplicationContext();
        context.register(SchedulerConfig.class);
        context.refresh();

        Object executorBean = context.getBean("replicationPushExecutor");
        assertThat(executorBean).isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(ReflectionTestUtils.getField(executorBean, "corePoolSize")).isEqualTo(4);
        assertThat(ReflectionTestUtils.getField(executorBean, "maxPoolSize")).isEqualTo(4);
    }

    @Test
    void clockBean_isSystemUtc() {
        context = new AnnotationConfigApplicationContext();
        context.register(SchedulerConfig.class);
        context.refresh();

        assertThat(context.getBean(Clock.class)).isNotNull();
    }

    @Test
    void beans_respectPropertyOverrides() {
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "scheduler-config-test-overrides",
                Map.of(
                        "app.scheduler.pool-size", "2",
                        "app.replication.push-parallelism", "2"
                )));
        context.register(SchedulerConfig.class);
        context.refresh();

        Object taskSchedulerBean = context.getBean("taskScheduler");
        assertThat(taskSchedulerBean).isInstanceOf(ThreadPoolTaskScheduler.class);
        assertThat(ReflectionTestUtils.getField(taskSchedulerBean, "poolSize"))
                .as("taskScheduler poolSize should follow app.scheduler.pool-size override")
                .isEqualTo(2);

        Object executorBean = context.getBean("replicationPushExecutor");
        assertThat(executorBean).isInstanceOf(ThreadPoolTaskExecutor.class);
        assertThat(ReflectionTestUtils.getField(executorBean, "corePoolSize"))
                .as("replicationPushExecutor corePoolSize should follow app.replication.push-parallelism override")
                .isEqualTo(2);
        assertThat(ReflectionTestUtils.getField(executorBean, "maxPoolSize"))
                .as("replicationPushExecutor maxPoolSize should follow app.replication.push-parallelism override")
                .isEqualTo(2);
    }
}
