package com.gameplatform.central.infrastructure.config;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Classe di configurazione che abilita globalmente l'esecuzione di task
 * schedulati tramite Spring all'interno del central-system.
 *
 * <p>Grazie all'annotazione {@link EnableScheduling}, il container Spring
 * rileva automaticamente qualsiasi metodo di bean annotato con
 * {@code @Scheduled} (come in {@code UserReplicationSchedulerService}) e lo
 * registra per l'esecuzione asincrona in background.</p>
 *
 * <p>Isolare questa configurazione in una classe dedicata rispetta il
 * principio di responsabilità singola (SRP), separando l'attivazione della
 * schedulazione dal bootstrapper principale dell'applicazione. Ciò permette
 * di personalizzare facilmente la schedulazione (ad esempio configurando un
 * bean {@code ThreadPoolTaskScheduler} personalizzato) o di disabilitarla
 * durante i test di integrazione.</p>
 *
 * <p>Dichiara due bean per la gestione dei thread pool:</p>
 * <ul>
 *   <li>{@code taskScheduler} — un {@link ThreadPoolTaskScheduler} con
 *       dimensione del pool configurabile (default {@code 4}). Spring risolve
 *       il {@code TaskScheduler} sottostante i metodi {@code @Scheduled}
 *       cercando un bean chiamato {@code taskScheduler}; dichiararlo qui
 *       sostituisce il fallback implicito a singolo thread
 *       {@code ConcurrentTaskScheduler}, permettendo a più metodi
 *       {@code @Scheduled} di eseguire in concorrenza.</li>
 *   <li>{@code replicationPushExecutor} — un {@link ThreadPoolTaskExecutor}
 *       dedicato, utilizzato da {@code UserReplicationSchedulerService} per
 *       inviare eventi di replica utente a ogni server locale attivo in
 *       parallelo.</li>
 * </ul>
 *
 * @see org.springframework.scheduling.annotation.EnableScheduling
 * @see org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
 * @see org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {

    /**
     * Restituisce l'orologio di sistema UTC utilizzato per la generazione dei
     * timestamp nei task schedulati.
     *
     * @return orologio di sistema nel fuso orario UTC
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Crea e restituisce il {@link ThreadPoolTaskScheduler} multi-thread che
     * gestisce l'esecuzione di tutti i metodi annotati con {@code @Scheduled}.
     *
     * <p>Il bean è nominato esplicitamente {@code taskScheduler} poiché questo
     * è il nome convenzionale che l'infrastruttura {@code @Scheduled} di Spring
     * utilizza per risolvere un {@code TaskScheduler}. In assenza di questo
     * bean, Spring adotta un {@code ConcurrentTaskScheduler} a singolo thread,
     * serializzando di fatto tutti i task schedulati.</p>
     *
     * @param poolSize numero di thread dello scheduler, specificato dalla proprietà
     *                 {@code app.scheduler.pool-size} (default 4)
     * @return lo scheduler multi-thread configurato
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
     * Crea e restituisce l'esecutore dedicato per le operazioni di push
     * parallelo verso i server locali, utilizzato da
     * {@code UserReplicationSchedulerService}.
     *
     * <p>Mantenere questo esecutore separato da {@link #taskScheduler(int)}
     * evita che operazioni I/O bloccanti verso i server locali possano
     * affamare i metodi {@code @Scheduled}, e viceversa.</p>
     *
     * @param parallelism numero di thread core e massimi del pool, specificato
     *                    dalla proprietà {@code app.replication.push-parallelism}
     *                    (default 4)
     * @return l'esecutore configurato per la replica dei dati
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

