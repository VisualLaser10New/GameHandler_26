package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * R3 (outbox atomicity) — atomic abort-and-emit helper.
 *
 * <p>Holds the body that previously lived inline in {@link HealthCheckService}'s
 * TIMEOUT branch (where it was wrapped in a {@code try { ... } catch (Exception e)
 * { log.error(...); }} that swallowed any failure from {@code objectMapper.write
 * ValueAsString} or {@code outboxEventRepository.save}). That swallow let the
 * class-level {@code @Transactional} on {@code performHealthCheck} commit the
 * abort (session + game release) WITHOUT the matching {@code GAME_SESSION_ABORTED}
 * outbox row, leaving central statistics permanently understated.</p>
 *
 * <p>This bean runs the abort + game release + outbox emission inside its own
 * {@link Propagation#REQUIRES_NEW} transaction. ANY exception (serialization
 * failure, save failure, transition guard violation) propagates out and rolls
 * back the ENTIRE transaction → session NOT aborted, game NOT released, NO
 * outbox row. Callers (e.g. {@link HealthCheckService}) wrap the call in a
 * {@code try/catch} that merely logs — the rollback is the desired contract,
 * and the next heartbeat tick retries.</p>
 *
 * <p>The bean is a SEPARATE Spring component (not a self-invoked private method)
 * because Spring's {@code @Transactional} proxy does not intercept
 * {@code this}-internal calls — REQUIRES_NEW via self-invocation is a no-op.
 * Calling a sibling bean is the only way the new tx actually opens.</p>
 *
 * <p>The body mirrors the previous {@link SessionRecoveryHelper#abortSession}
 * implementation exactly (abort → save session → release game + save → defer
 * publishState afterCommit → build payload + save outbox), generalised to take
 * a {@link StopReason} and a {@code stopReasonCode} string for the outbox
 * payload so the same atomic unit serves both the heartbeat-TIMEOUT path
 * (HealthCheckService) and the SERVER_RESTART recovery path
 * (SessionRecoveryService via SessionRecoveryHelper).</p>
 */
@Component
public class SessionAbortHelper {

    private static final Logger log = LoggerFactory.getLogger(SessionAbortHelper.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public SessionAbortHelper(
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void abortAndEmit(GameSession session, StopReason stopReason, String stopReasonCode) throws Exception {
        // WAITING sessions are lobbies that never started; cancel via cancelLobby
        // (which transitions WAITING → ABORTED with winCondition=TIMEOUT). Anything
        // else (IN_PROGRESS / PAUSED) goes through the regular abort path.
        if (session.getStatus() == GameStatus.WAITING) {
            session.cancelLobby(Instant.now(clock));
        } else {
            session.abort(stopReason, Instant.now(clock));
        }
        gameSessionRepository.save(session);

        // Release the game machine if it exists. Mirrors the previous
        // SessionRecoveryHelper behaviour: a missing game is tolerated (the
        // outbox event is still emitted so central stats are correct).
        Game game = gameRepository.findById(session.getGameId()).orElse(null);
        if (game != null) {
            game.release();
            gameRepository.save(game);

            // MQTT publish must NOT run inside the tx before commit — it is
            // deferred to afterCommit so a publish failure never leaks a
            // half-committed state machine mutation. When invoked outside a
            // Spring tx (e.g. plain Mockito tests) the publish runs inline.
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
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

        // Build + persist the GAME_SESSION_ABORTED outbox row. Any failure here
        // (objectMapper / save) propagates and rolls back the entire tx — the
        // whole point of R3.
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("occurredAt", Instant.now(clock).toString());
        payload.put("sessionId", session.getId().value());
        payload.put("gameType", session.getGameType().name());
        payload.put("durationSeconds", session.getDurationSeconds());
        payload.put("status", session.getStatus().name());
        payload.put("stopReason", stopReasonCode);

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
