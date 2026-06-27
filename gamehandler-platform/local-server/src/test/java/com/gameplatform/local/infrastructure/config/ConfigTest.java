package com.gameplatform.local.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

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
}