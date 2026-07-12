package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.StopReason;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.TournamentMatchResultDto;
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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;

    public SessionAbortHelper(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock,
            ObjectMapper objectMapper,
            TournamentMatchLocalRepository tournamentMatchLocalRepository) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
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

        // FASE 6 — when the session is bound to a tournament match, emit a
        // second outbox row TOURNAMENT_MATCH_COMPLETED with status="ABANDONED"
        // and a walkover winner (the match participant NOT in the session's
        // participants), then flip the local match row to ABANDONED. Atomic in
        // this REQUIRES_NEW tx. Per Q2: winner = walkoverWinner (NOT null) so
        // the central advanceWinner receives a non-null winnerId and the
        // tournament flows normally.
        if (session.getTournamentMatchId() != null) {
            TournamentMatchLocal localMatch = tournamentMatchLocalRepository
                    .findById(session.getTournamentMatchId())
                    .orElse(null);
            if (localMatch == null) {
                log.warn("Tournament match {} not found locally while aborting session {}",
                        session.getTournamentMatchId().value(), session.getId().value());
            } else {
                Set<String> sessionParticipantValues = session.getParticipants().stream()
                        .map(UserId::value).collect(Collectors.toSet());
                String participantA = localMatch.getParticipantA();
                String participantB = localMatch.getParticipantB();
                String walkoverWinner;
                if (participantB == null || participantB.isBlank()) {
                    // Edge case: participantB is null (BYE — but BYEs shouldn't
                    // have sessions) → walkover winner = participantA.
                    walkoverWinner = participantA;
                } else if (sessionParticipantValues.contains(participantA)) {
                    // participantA is the abandoning side → walkover winner = participantB.
                    walkoverWinner = participantB;
                } else {
                    // participantA not in the session's participants → it is the
                    // walkover winner (the abandoning side is participantB, which IS
                    // in the session). This also covers the team pseudo-participant
                    // case where session.getParticipants() is [participantB].
                    walkoverWinner = participantA;
                }

                TournamentMatchResultDto tournamentDto = new TournamentMatchResultDto(
                        session.getTournamentMatchId().value(),
                        walkoverWinner,
                        null,
                        "ABANDONED"
                );
                String tournamentPayloadJson = objectMapper.writeValueAsString(tournamentDto);
                OutboxEvent tournamentOutboxEvent = new OutboxEvent(
                        UUID.randomUUID().toString(),
                        "TOURNAMENT_MATCH_COMPLETED",
                        tournamentPayloadJson,
                        "PENDING",
                        Instant.now(clock),
                        null,
                        0
                );
                outboxEventRepository.save(tournamentOutboxEvent);

                // Flip the local match to ABANDONED.
                tournamentMatchLocalRepository.save(localMatch.withStatus(TournamentMatchStatus.ABANDONED));
            }
        }
    }
}
