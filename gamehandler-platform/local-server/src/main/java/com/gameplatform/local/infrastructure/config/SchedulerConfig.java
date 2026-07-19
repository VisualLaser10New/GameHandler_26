package com.gameplatform.local.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Configurazione del bean {@link Clock} per l'ottenimento del timestamp UTC corrente.
 * <p>
 * Fornisce un orologio di sistema UTC utilizzabile dai componenti che necessitano
 * di un riferimento temporale coerente e testabile.
 * </p>
 *
 * @see java.time.Clock
 */
@Configuration
public class SchedulerConfig {

    /**
     * Fornisce un orologio di sistema configurato sul fuso orario UTC.
     *
     * @return un'istanza di {@link Clock} basata sull'orologio di sistema UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

