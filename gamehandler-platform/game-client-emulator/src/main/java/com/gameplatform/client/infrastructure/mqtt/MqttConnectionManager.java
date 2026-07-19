package com.gameplatform.client.infrastructure.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Gestisce il ciclo di vita e la salute di una connessione MQTT.
 * <p>
 * All'avvio tramite {@link #start()}, il gestore tenta una connessione iniziale
 * con tentativi ogni {@value #RECONNECT_DELAY_SECONDS} secondi fino al successo.
 * Un controllo periodico di salute viene eseguito ogni
 * {@value #HEALTH_CHECK_INTERVAL_SECONDS} secondi; se la connessione viene persa,
 * viene attivata automaticamente la riconnessione.
 * <p>
 * Supporta l'arresto graduale tramite {@link #stop()} e {@link #shutdown()}
 * per la pulizia delle risorse.
 */
public class MqttConnectionManager {

    private static final Logger log = LoggerFactory.getLogger(MqttConnectionManager.class);

    private static final long RECONNECT_DELAY_SECONDS = 10;
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 30;

    private final MqttClientAdapter adapter;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;

    /**
     * Costruisce un gestore di connessione per l'adapter specificato.
     *
     * @param adapter l'{@link MqttClientAdapter} da gestire
     */
    public MqttConnectionManager(MqttClientAdapter adapter) {
        this.adapter = adapter;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = new AtomicBoolean(false);
    }

    /**
     * Avvia il gestore di connessione.
     * <p>
     * Esegue una connessione iniziale bloccante con tentativi, quindi programma
     * un controllo periodico di salute per rilevare e recuperare la perdita di
     * connessione. Questo metodo è idempotente; chiamate successive vengono
     * ignorate se gi&agrave; in esecuzione.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting MQTT connection manager");
            connectWithRetry();
            scheduler.scheduleAtFixedRate(this::checkConnection, HEALTH_CHECK_INTERVAL_SECONDS, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }
    }

    /**
     * Arresta il gestore di connessione.
     * <p>
     * Cancella lo scheduler del controllo di salute e disconnette il client MQTT.
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
     * Tenta la connessione al broker, riprovando ogni
     * {@value #RECONNECT_DELAY_SECONDS} secondi fino al successo o fino
     * all'arresto del gestore.
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
     * Callback periodico di controllo di salute.
     * Attiva la riconnessione se il client MQTT non è pi&ugrave; connesso.
     */
    private void checkConnection() {
        if (running.get() && !adapter.isConnected()) {
            log.warn("MQTT connection lost, attempting reconnection...");
            connectWithRetry();
        }
    }

    /**
     * Restituisce {@code true} se il client MQTT è attualmente connesso.
     *
     * @return {@code true} se connesso, {@code false} altrimenti
     */
    public boolean isConnected() {
        return adapter.isConnected();
    }

    /**
     * Restituisce l'adapter sottostante gestito da questo gestore.
     *
     * @return l'{@link MqttClientAdapter} gestito
     */
    public MqttClientAdapter getAdapter() {
        return adapter;
    }

    /**
     * Esegue un arresto graduale dello scheduler.
     * <p>
     * Attende fino a 5 secondi per il completamento delle attività in sospeso
     * prima di forzare l'arresto.
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
