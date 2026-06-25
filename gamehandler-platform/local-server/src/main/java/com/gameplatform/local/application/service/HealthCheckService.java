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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class HealthCheckService {

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
                    // Abort any active sessions
                    Optional<GameSession> activeSessionOpt = gameSessionRepository.findActiveByGameId(gameId);
                    if (activeSessionOpt.isPresent()) {
                        GameSession session = activeSessionOpt.get();
                        session.abort(StopReason.TIMEOUT, Instant.now(clock));
                        gameSessionRepository.save(session);

                        // Generate outbox sync event
                        try {
                            Map<String, Object> payload = Map.of(
                                    "eventId", UUID.randomUUID().toString(),
                                    "occurredAt", Instant.now(clock).toString(),
                                    "sessionId", session.getId().value(),
                                    "gameType", session.getGameType().name(),
                                    "durationSeconds", session.getDurationSeconds(),
                                    "status", session.getStatus().name(),
                                    "stopReason", "TIMEOUT"
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

                    // Release game machine if it was IN_USE or RESERVED or MAINTENANCE
                    if (game.getStatus() != GameMachineStatus.AVAILABLE) {
                        game.release();
                        gameRepository.save(game);
                        publishGameStatePort.publishState(gameId, game.getStatus());
                    }

                    // Publish alert to MQTT
                    AlertPayload alert = new AlertPayload(
                            "UNREACHABLE",
                            gameId.id(),
                            "Client has missed 3 consecutive heartbeat cycles (15 minutes). Declaring unreachable.",
                            Instant.now(clock)
                    );
                    publishAlertPort.publishAlert(alert);
                }
            } else {
                // Reset missed counter on successful contact
                missedHeartbeatsMap.put(gameId, 0);
            }

            // Reset response flag for the next cycle
            respondedInCycle.put(gameId, false);

            // Send new heartbeat ping to client via MQTT session event topic
            String topic = MqttTopics.heartbeat(game.getBuildingId().id(), gameId.id());
            publishGameStatePort.publishSessionEvent(topic, "PING");
        }
    }

    public void registerHeartbeat(GameId gameId) {
        respondedInCycle.put(gameId, true);
    }
}
