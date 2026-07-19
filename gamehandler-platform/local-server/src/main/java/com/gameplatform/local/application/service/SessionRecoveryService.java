package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servizio di recupero sessioni all'avvio dell'applicazione. Implementa
 * {@link SmartLifecycle} per eseguire il recupero dopo l'inizializzazione
 * del contesto Spring e del client MQTT. Invia heartbeat PING a tutte le
 * macchine con sessioni attive (IN_PROGRESS/PAUSED) e, dopo 30 secondi
 * di attesa, abortisce le sessioni che non hanno risposto.
 *
 * @see SessionRecoveryHelper
 * @see GameSessionRepository
 */
@Service
@DependsOn("mqttClient")
public class SessionRecoveryService implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(SessionRecoveryService.class);

    private final GameSessionRepository gameSessionRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final SessionRecoveryHelper sessionRecoveryHelper;

    private final ConcurrentHashMap<GameId, Boolean> pendingAcks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread recoveryThread;

    /**
     * Costruisce il servizio di recupero sessioni con le dipendenze
     * necessarie per l'invio degli heartbeat e la gestione delle risposte.
     *
     * @param gameSessionRepository  il repository delle sessioni di gioco
     * @param publishGameStatePort   il port per la pubblicazione dello stato di gioco
     * @param sessionRecoveryHelper  l'helper per l'aborto delle sessioni non rispondenti
     */
    public SessionRecoveryService(
            GameSessionRepository gameSessionRepository,
            PublishGameStatePort publishGameStatePort,
            SessionRecoveryHelper sessionRecoveryHelper) {
        this.gameSessionRepository = gameSessionRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.sessionRecoveryHelper = sessionRecoveryHelper;
    }

    /**
     * Avvia il recupero sessioni in un thread asincrono per non bloccare
     * l'avvio principale dell'applicazione.
     */
    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // Run recovery asynchronously to avoid blocking the main thread during application startup
        recoveryThread = new Thread(this::recoverSessions, "session-recovery-thread");
        recoveryThread.start();
    }

    /**
     * Recupera le sessioni attive (IN_PROGRESS e PAUSED) all'avvio
     * dell'applicazione. Invia un heartbeat PING a ogni macchina da
     * gioco con sessione attiva e, dopo 30 secondi di attesa, abortisce
     * le sessioni che non hanno risposto. Il metodo viene eseguito in un
     * thread asincrono separato.
     */
    private void recoverSessions() {
        try {
            List<GameSession> activeSessions = new ArrayList<>();
            activeSessions.addAll(gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS));
            activeSessions.addAll(gameSessionRepository.findByStatus(GameStatus.PAUSED));

            if (activeSessions.isEmpty()) {
                running.set(false);
                return;
            }

            // Send heartbeat ping to each active/paused game machine
            for (GameSession session : activeSessions) {
                pendingAcks.put(session.getGameId(), false);
                String topic = MqttTopics.heartbeat(session.getBuildingId().id(), session.getGameId().id());
                publishGameStatePort.publishSessionEvent(topic, "RECOVERY_PING");
            }

            // Wait 30 seconds for responses
            Thread.sleep(30000);

            // Abort sessions that did not respond
            for (GameSession session : activeSessions) {
                Boolean ackReceived = pendingAcks.get(session.getGameId());
                if (ackReceived == null || !ackReceived) {
                    try {
                        sessionRecoveryHelper.abortSession(session);
                    } catch (Exception e) {
                        log.error("Failed to abort session during recovery for session: {}", session.getId(), e);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pendingAcks.clear();
            running.set(false);
        }
    }

    /**
     * Interrompe il thread di recupero e ferma il servizio.
     */
    @Override
    public void stop() {
        running.set(false);
        if (recoveryThread != null) {
            recoveryThread.interrupt();
        }
    }

    /**
     * Verifica se il servizio di recupero e' attualmente in esecuzione.
     *
     * @return true se il thread di recupero e' attivo, false altrimenti
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Restituisce la fase di avvio del servizio. Il valore massimo
     * garantisce che il recupero venga eseguito dopo l'inizializzazione
     * di tutti gli altri componenti, incluso il client MQTT.
     *
     * @return Integer.MAX_VALUE per un avvio in fase tardiva
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    /**
     * Verifica se il servizio deve essere avviato automaticamente.
     *
     * @return true (avvio automatico)
     */
    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /**
     * Registra un acknowledgement di heartbeat per una macchina da gioco
     * durante la fase di recupero, impedendone l'abort.
     *
     * @param gameId l'identificativo della macchina che ha risposto
     */
    public void registerHeartbeatAck(GameId gameId) {
        if (pendingAcks.containsKey(gameId)) {
            pendingAcks.put(gameId, true);
        }
    }
}
