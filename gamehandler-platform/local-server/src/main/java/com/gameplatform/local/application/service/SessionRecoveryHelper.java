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
import java.util.Map;
import java.util.UUID;

@Component
@Transactional
public class SessionRecoveryHelper {

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
            publishGameStatePort.publishState(game.getId(), game.getStatus());
        }

        // Generate outbox sync event
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
    }
}
