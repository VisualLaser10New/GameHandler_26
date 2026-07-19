package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.in.RegisterLocalServerUseCase;
import com.gameplatform.local.domain.ports.out.RegisterLocalServerPort;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servizio che auto-registra il server locale presso il sistema centrale
 * all'avvio dell'applicazione, con backoff esponenziale e jitter.
 * Implementa {@link SmartLifecycle} per eseguire la registrazione dopo
 * che il contesto Spring e' completamente inizializzato.
 *
 * <p>Politica di retry: delay iniziale 1s, fattore esponenziale 2, delay
 * massimo 30s, jitter ±20%. Il ciclo termina non appena
 * {@link RegisterLocalServerPort#register()} restituisce true, o quando
 * il thread viene interrotto.</p>
 *
 * @see RegisterLocalServerUseCase
 * @see RegisterLocalServerPort
 * @see SyncSchedulerService
 */
@Service
public class LocalServerRegistrationService implements RegisterLocalServerUseCase, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(LocalServerRegistrationService.class);

    static final long INITIAL_DELAY_MS = 1_000L;
    static final long MAX_DELAY_MS = 30_000L;
    static final double JITTER = 0.2d;

    private final RegisterLocalServerPort registerLocalServerPort;
    private final SyncCentralSystemPort syncCentralSystemPort;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean registered = new AtomicBoolean(false);
    private volatile Thread worker;

    public LocalServerRegistrationService(
            RegisterLocalServerPort registerLocalServerPort,
            SyncCentralSystemPort syncCentralSystemPort) {
        this.registerLocalServerPort = registerLocalServerPort;
        this.syncCentralSystemPort = syncCentralSystemPort;
    }

    /**
     * Esegue la registrazione verso il sistema centrale con backoff
     * esponenziale e jitter fino al successo o all'interruzione del
     * thread.
     *
     * @return true se la registrazione e' avvenuta con successo
     */
    @Override
    public boolean register() {
        long delay = INITIAL_DELAY_MS;
        while (!Thread.currentThread().isInterrupted() && !registered.get()) {
            if (syncCentralSystemPort.isReachable()) {
                if (registerLocalServerPort.register()) {
                    registered.set(true);
                    log.info("Local server registered with central system.");
                    return true;
                }
                log.warn("Central system reachable but registration rejected; will retry.");
            } else {
                log.info("Central system not reachable; will retry registration in {} ms.", delay);
            }
            sleepWithJitter(delay);
            delay = Math.min(MAX_DELAY_MS, delay * 2);
        }
        return registered.get();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // SmartLifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void start() {
        running.set(true);
        worker = new Thread(this::register, "local-server-registration");
        worker.setDaemon(true);
        worker.start();
    }

    @Override
    public void stop() {
        running.set(false);
        Thread w = worker;
        if (w != null) {
            w.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Run AFTER Spring context refresh and after most beans (schedulers, MQTT)
        return Integer.MAX_VALUE - 10;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Verifica se la registrazione e' stata completata con successo.
     * Visibile per test.
     *
     * @return true se il server e' registrato
     */
    // visible for testing
    boolean isRegistered() {
        return registered.get();
    }

    /**
     * Pone il thread in sleep per il tempo specificato con l'aggiunta
     * di jitter casuale del ±20%.
     *
     * @param baseDelayMs il delay base in millisecondi
     */
    private void sleepWithJitter(long baseDelayMs) {
        long jitter = (long) (baseDelayMs * JITTER * (Math.random() - 0.5) * 2);
        long total = Math.max(100L, baseDelayMs + jitter);
        try {
            TimeUnit.MILLISECONDS.sleep(total);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
