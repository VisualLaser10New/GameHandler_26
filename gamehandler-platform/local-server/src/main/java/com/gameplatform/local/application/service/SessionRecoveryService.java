package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishAlertPort;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.mqtt.MqttTopics;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@DependsOn("mqttClient")
public class SessionRecoveryService implements SmartLifecycle {

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final PublishAlertPort publishAlertPort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<GameId, Boolean> pendingAcks = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SessionRecoveryService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            PublishAlertPort publishAlertPort,
            Clock clock,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.publishAlertPort = publishAlertPort;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // Run recovery asynchronously to avoid blocking the main thread during application startup
        new Thread(this::recoverSessions, "session-recovery-thread").start();
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
                    // Client didn't respond: abort session
                    session.abort(StopReason.ABORTED, Instant.now(clock));
                    gameSessionRepository.save(session);

                    Game game = gameRepository.findById(session.getGameId()).orElse(null);
                    if (game != null) {
                        game.release();
                        gameRepository.save(game);
                        publishGameStatePort.publishState(game.getId(), game.getStatus());
                    }

                    // Generate outbox sync event
                    try {
                        Map<String, Object> payload = Map.of(
                                "eventId", UUID.randomUUID().toString(),
                                "occurredAt", Instant.now(clock).toString(),
                                "sessionId", session.getId().value(),
                                "gameType", session.getGameType().name(),
                                "durationSeconds", session.getDurationSeconds(),
                                "status", session.getStatus().name(),
                                "stopReason", "SERVER_RESTART"
                        );
                        String payloadJson = objectMapper.writeValueAsString(payload);

                        OutboxEvent outboxEvent = new OutboxEvent(
                                UUID.randomUUID().toString(),
                                "GAME_SESSION_COMPLETED",
                                payloadJson,
                                "PENDING",
                                Instant.now(clock),
                                null,
                                0
                        );
                        outboxEventRepository.save(outboxEvent);
                    } catch (Exception e) {
                        e.printStackTrace();
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
