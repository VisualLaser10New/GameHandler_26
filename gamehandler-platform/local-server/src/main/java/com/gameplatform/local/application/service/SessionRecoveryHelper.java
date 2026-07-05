package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.StopReason;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Transactional
public class SessionRecoveryHelper {

    private static final Logger log = LoggerFactory.getLogger(SessionRecoveryHelper.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SessionRecoveryHelper(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    public void abortSession(GameSession session) throws Exception {
        // Client didn't respond: abort session
        session.abort(StopReason.ABORTED, Instant.now(clock));
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId()).orElse(null);
        if (game != null) {
            game.release();
            gameRepository.save(game);
            
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                publishGameStatePort.publishState(game.getId(), game.getStatus());
                            } catch (Exception e) {
                                log.error("Failed to publish game state after transaction commit", e);
                            }
                        }
                    }
                );
            } else {
                publishGameStatePort.publishState(game.getId(), game.getStatus());
            }
        }

        // Generate outbox sync event
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now(clock).toString());
        payload.put("sessionId", session.getId().value());
        payload.put("gameType", session.getGameType().name());
        payload.put("durationSeconds", session.getDurationSeconds());
        payload.put("status", session.getStatus().name());
        payload.put("stopReason", "SERVER_RESTART");

        String payloadJson = objectMapper.writeValueAsString(payload);

        OutboxEvent outboxEvent = new OutboxEvent(
                UUID.randomUUID().toString(),
                "GAME_SESSION_ABORTED",
                payloadJson,
                "PENDING",
                Instant.now(clock),
                null,
                0
        );
        outboxEventRepository.save(outboxEvent);
    }
}
