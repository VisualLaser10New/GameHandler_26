package com.gameplatform.client.infrastructure.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle and health of an MQTT connection.
 * <p>
 * On {@link #start()}, the manager attempts an initial connection with
 * retries every {@value #RECONNECT_DELAY_SECONDS} seconds until successful.
 * A periodic health check runs every {@value #HEALTH_CHECK_INTERVAL_SECONDS}
 * seconds; if the connection is lost, it triggers reconnection automatically.
 * <p>
 * Supports graceful {@link #stop()} and {@link #shutdown()} for resource cleanup.
 */
public class MqttConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(MqttConnectionManager.class);

    private static final long RECONNECT_DELAY_SECONDS = 10;
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 30;

    private final MqttClientAdapter adapter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    /**
     * Creates a connection manager for the given adapter.
     *
     * @param adapter the {@link MqttClientAdapter} to manage
     */
    public MqttConnectionManager(MqttClientAdapter adapter) {
        this.adapter = adapter;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Starts the connection manager.
     * <p>
     * Performs an initial blocking connection with retry, then schedules
     * a periodic health check to detect and recover from connection loss.
     * This method is idempotent; subsequent calls are ignored while running.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting MQTT connection manager");
            connectWithRetry();
            scheduler.scheduleAtFixedRate(this::checkConnection, HEALTH_CHECK_INTERVAL_SECONDS, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Stops the connection manager.
     * <p>
     * Cancels the health check scheduler and disconnects the MQTT client.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping MQTT connection manager");
            scheduler.shutdownNow();
            try {
                adapter.disconnect();
            } catch (MqttException e) {
                log.error("Error disconnecting MQTT client", e);
            }
        }
    }

    /**
     * Attempts to connect to the broker, retrying every
     * {@value #RECONNECT_DELAY_SECONDS} seconds until successful or
     * the manager is stopped.
     */
    private void connectWithRetry() {
        while (running.get() && !adapter.isConnected()) {
            try {
                log.info("Attempting to connect to MQTT broker...");
                adapter.connect();
                log.info("MQTT connection established successfully");
            } catch (MqttException e) {
                log.warn("MQTT connection failed (retry in {}s): {}", RECONNECT_DELAY_SECONDS, e.getMessage());
                try {
                    Thread.sleep(RECONNECT_DELAY_SECONDS * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * Periodic health check callback. Triggers reconnection if the
     * MQTT client is no longer connected.
     */
    private void checkConnection() {
        if (running.get() && !adapter.isConnected()) {
            log.warn("MQTT connection lost, attempting reconnection...");
            connectWithRetry();
        }
    }

    /**
     * Returns whether the MQTT client is currently connected.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return adapter.isConnected();
    }

    /**
     * Returns the underlying adapter.
     *
     * @return the {@link MqttClientAdapter} managed by this manager
     */
    public MqttClientAdapter getAdapter() {
        return adapter;
    }

    /**
     * Performs a graceful shutdown of the scheduler.
     * <p>
     * Waits up to 5 seconds for pending tasks to complete before
     * forcing shutdown.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
