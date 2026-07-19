package com.gameplatform.client.application.service;

import com.gameplatform.client.infrastructure.mqtt.HeartbeatPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Servizio che invia heartbeat periodici per una partita in corso.
 * Pubblica messaggi heartbeat tramite MQTT a un intervallo configurato
 * e gestisce il ciclo di vita dello scheduler dedicato.
 */
public class HeartbeatService extends Service {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatService.class);
    private static final long HEARTBEAT_INTERVAL_SECONDS = 5;
    private final HeartbeatPublisher heartbeatPublisher;
    private volatile String currentGameId;

    /**
     * Costruisce un nuovo servizio heartbeat.
     *
     * @param heartbeatPublisher il publisher MQTT per l'invio degli heartbeat, non null
     */
    public HeartbeatService(HeartbeatPublisher heartbeatPublisher) {
        this.heartbeatPublisher = heartbeatPublisher;
    }

    /**
     * Avvia l'invio periodico di heartbeat per il gioco specificato.
     * Se un heartbeat è già in corso, lo arresta prima di avviarne uno nuovo.
     *
     * @param gameId l'identificativo del gioco per cui inviare heartbeat, non null
     */
    public void startHeartbeat(String gameId) {
        stopHeartbeat(); // ensure clean state before starting

        this.currentGameId = gameId;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "heartbeat-scheduler-" + gameId);
            t.setDaemon(true);
            return t;
        });

        scheduledTask = scheduler.scheduleAtFixedRate(
                this::handleHeartbeat,
                0,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );

        log.info("Heartbeat started for game {} (interval: {}s)", gameId, HEARTBEAT_INTERVAL_SECONDS);
    }

    /**
     * Arresta l'invio periodico di heartbeat e rilascia le risorse dello scheduler.
     * Azzera l'identificativo del gioco tracciato.
     */
    public void stopHeartbeat() {
        stopService();
        log.info("Heartbeat stopped for game {}", currentGameId);
        currentGameId = null;
    }



    /**
     * Pubblica un heartbeat per il gioco correntemente tracciato.
     * Se nessun gioco è in corso, registra un avviso e non esegue alcuna operazione.
     */
    public void handleHeartbeat() {
        if (currentGameId == null) {
            log.warn("handleHeartbeat called but no game is currently tracked");
            return;
        }
        log.debug("Sending heartbeat for game {}", currentGameId);
        heartbeatPublisher.publishHeartbeat(currentGameId);
    }

    /**
     * Verifica se il servizio heartbeat è attualmente in esecuzione.
     *
     * @return true se lo scheduler è attivo e non è stato arrestato, false altrimenti
     */
    public boolean isRunning() {
        return scheduler != null && !scheduler.isShutdown();
    }
}