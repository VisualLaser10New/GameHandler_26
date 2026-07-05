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
 * Application service that self-registers the local server against the central system
 * at application startup, with exponential backoff and jitter.
 *
 * <p>Implements {@link SmartLifecycle} so registration runs after the Spring context
 * is fully refreshed (i.e. after MQTT, scheduler and adapters are wired). The
 * {@code stop()} callback interrupts the worker thread: registration is a startup
 * operation; if the central is down the app still starts and the periodic
 * {@link SyncSchedulerService} keeps retrying sync, while a follow-up registration
 * will happen when the central comes back (the central's {@code updateLastSeenAt}
 * is an upsert-safe heartbeat).</p>
 *
 * <p>Retry policy: initial delay 1s, exponential factor 2, max delay 30s, jitter ±20%.
 * The loop terminates as soon as {@link RegisterLocalServerPort#register()} returns true,
 * or when the running thread is interrupted.</p>
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

    // visible for testing
    boolean isRegistered() {
        return registered.get();
    }

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
