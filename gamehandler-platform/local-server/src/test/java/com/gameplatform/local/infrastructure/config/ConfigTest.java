package com.gameplatform.local.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.gameplatform.local.application.service.HealthCheckService;
import com.gameplatform.local.application.service.SyncSchedulerService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

class TlsConfigTest {

    @Test
    void sslContextBeanCreatedWithTlsProtocol() {
        try (var ctx = new AnnotationConfigApplicationContext(TlsConfig.class)) {
            javax.net.ssl.SSLContext ssl = ctx.getBean(javax.net.ssl.SSLContext.class);
            assertThat(ssl).isNotNull();
            assertThat(ssl.getProtocol()).isEqualTo("TLS");
        }
    }
}

class SchedulerConfigTest {

    @Test
    void clockBeanIsSystemUtc() {
        try (var ctx = new AnnotationConfigApplicationContext(SchedulerConfig.class)) {
            java.time.Clock clock = ctx.getBean(java.time.Clock.class);
            assertThat(clock).isNotNull();
            assertThat(clock.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
        }
    }

    /**
     * B6.3 — Verifies the {@code @Scheduled} annotation on the sync and health-check
     * cadence methods references the externally-configurable property placeholders
     * (default {@code 300000} ms = 5 minutes). The presence of the placeholder is what
     * allows {@code app.sync-interval-ms=10000} (or any other value) to override the
     * cadence at runtime via {@code application.yml} / env vars without code changes —
     * i.e. the cadence is configurable, not hardcoded.
     *
     * <p>Annotation reflection is used (rather than spinning up a real scheduler) because
     * it is deterministic and not timing-dependent: a real scheduler spin-up would be
     * flaky (the fixed-delay cadence fires only after the previous invocation completes,
     * and asserting "fired exactly N times in window W" is inherently racy). The
     * placeholder string in the annotation is the single source of truth for which
     * property governs the cadence, and overriding it (e.g.
     * {@code app.sync-interval-ms=10000}) is resolved by Spring's placeholder mechanism at
     * bean initialization — proven equivalent by the integration tests that boot the full
     * context with the test profile's {@code 999999999} value.</p>
     */
    @Test
    void syncSchedulerRespectsSyncIntervalMsProperty() throws Exception {
        Method syncMethod = SyncSchedulerService.class.getMethod("syncWithCentral");
        Scheduled syncScheduled = syncMethod.getAnnotation(Scheduled.class);
        assertThat(syncScheduled).as("@Scheduled must be present on syncWithCentral()").isNotNull();
        assertThat(syncScheduled.fixedDelayString())
                .isEqualTo("${app.sync-interval-ms:300000}");

        Method healthMethod = HealthCheckService.class.getMethod("performHealthCheck");
        Scheduled healthScheduled = healthMethod.getAnnotation(Scheduled.class);
        assertThat(healthScheduled).as("@Scheduled must be present on performHealthCheck()").isNotNull();
        assertThat(healthScheduled.fixedRateString())
                .isEqualTo("${app.healthcheck-interval-ms:300000}");
    }
}