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

    public SessionRecoveryService(
            GameSessionRepository gameSessionRepository,
            PublishGameStatePort publishGameStatePort,
            SessionRecoveryHelper sessionRecoveryHelper) {
        this.gameSessionRepository = gameSessionRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.sessionRecoveryHelper = sessionRecoveryHelper;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // Run recovery asynchronously to avoid blocking the main thread during application startup
        recoveryThread = new Thread(this::recoverSessions, "session-recovery-thread");
        recoveryThread.start();
    }

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

    @Override
    public void stop() {
        running.set(false);
        if (recoveryThread != null) {
            recoveryThread.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        // Start late in the startup phase (ensuring everything else is up)
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    public void registerHeartbeatAck(GameId gameId) {
        if (pendingAcks.containsKey(gameId)) {
            pendingAcks.put(gameId, true);
        }
    }
}
