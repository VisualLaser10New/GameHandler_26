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
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.mqtt.MqttTopics;
import com.gameplatform.shared.mqtt.payload.AlertPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(HealthCheckService.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final PublishAlertPort publishAlertPort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    // Tracks responded clients within the current 5-minute cycle
    private final ConcurrentHashMap<GameId, Boolean> respondedInCycle = new ConcurrentHashMap<>();

    // Tracks consecutive missed heartbeats for each game machine
    private final ConcurrentHashMap<GameId, Integer> missedHeartbeatsMap = new ConcurrentHashMap<>();

    public HealthCheckService(
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

    private void deferMqttPublish(Runnable publishRunnable) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishRunnable.run();
                        } catch (Exception e) {
                            log.error("Failed to execute deferred MQTT publication", e);
                        }
                    }
                }
            );
        } else {
            try {
                publishRunnable.run();
            } catch (Exception e) {
                log.error("Failed to execute MQTT publication", e);
            }
        }
    }

    @Scheduled(fixedRate = 300000)
    public void performHealthCheck() {
        List<Game> games = gameRepository.findAll();

        for (Game game : games) {
            GameId gameId = game.getId();
            
            // Check if client responded since last health check cycle
            boolean responded = respondedInCycle.getOrDefault(gameId, false);

            if (!responded) {
                int missed = missedHeartbeatsMap.getOrDefault(gameId, 0) + 1;
                missedHeartbeatsMap.put(gameId, missed);

                // If client failed to respond for 3 consecutive cycles (15 minutes), declare unreachable
                if (missed >= 3) {
                    missedHeartbeatsMap.put(gameId, 0);

                    // Abort any active sessions
                    Optional<GameSession> activeSessionOpt = gameSessionRepository.findActiveByGameId(gameId);
                    if (activeSessionOpt.isPresent()) {
                        GameSession session = activeSessionOpt.get();
                        session.abort(StopReason.TIMEOUT, Instant.now(clock));
                        gameSessionRepository.save(session);

                        // Generate outbox sync event
                        try {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("eventId", UUID.randomUUID().toString());
                            payload.put("occurredAt", Instant.now(clock).toString());
                            payload.put("sessionId", session.getId().value());
                            payload.put("gameType", session.getGameType().name());
                            payload.put("durationSeconds", session.getDurationSeconds());
                            payload.put("status", session.getStatus().name());
                            payload.put("stopReason", "TIMEOUT");

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
                            log.error("Failed to serialize or save outbox event during heartbeat health check", e);
                        }
                    }

                    // Release game machine only if its current status is IN_USE
                    if (game.getStatus() == GameMachineStatus.IN_USE) {
                        game.release();
                        gameRepository.save(game);
                        deferMqttPublish(() -> publishGameStatePort.publishState(gameId, game.getStatus()));
                    }

                    // Publish alert to MQTT
                    AlertPayload alert = new AlertPayload(
                            "UNREACHABLE",
                            gameId.id(),
                            "Client has missed 3 consecutive heartbeat cycles (15 minutes). Declaring unreachable.",
                            Instant.now(clock)
                    );
                    deferMqttPublish(() -> publishAlertPort.publishAlert(alert));
                }
            } else {
                // Reset missed counter on successful contact
                missedHeartbeatsMap.put(gameId, 0);
            }

            // Reset response flag for the next cycle
            respondedInCycle.put(gameId, false);

            // Send new heartbeat ping to client via MQTT session event topic
            String topic = MqttTopics.heartbeat(game.getBuildingId().id(), gameId.id());
            deferMqttPublish(() -> publishGameStatePort.publishSessionEvent(topic, "PING"));
        }
    }

    public void registerHeartbeat(GameId gameId) {
        respondedInCycle.put(gameId, true);
    }
}
