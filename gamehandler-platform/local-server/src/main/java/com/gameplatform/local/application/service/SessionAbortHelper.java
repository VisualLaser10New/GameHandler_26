package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.OutboxEventStatus;
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
 * Helper atomico di abort ed emissione evento. Esegue l'abort della
 * sessione, il rilascio della macchina da gioco e la pubblicazione
 * dell'evento outbox GAME_SESSION_ABORTED all'interno di una propria
 * transazione {@link Propagation#REQUIRES_NEW}. Qualsiasi eccezione
 * propaga il rollback dell'intera transazione — la sessione NON viene
 * abortita, la macchina NON viene rilasciata, NESSUNA riga outbox viene
 * scritta. I chiamanti (es. {@link HealthCheckService}) wrappano la
 * chiamata in un try/catch che logga e passa oltre; il tick successivo
 * ritenta.
 *
 * @see HealthCheckService
 * @see SessionRecoveryHelper
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

    /**
     * Esegue l'abort atomico di una sessione di gioco: transiziona la
     * sessione (WAITING {@literal ->} ABORTED via cancelLobby, oppure
     * IN_PROGRESS/PAUSED {@literal ->} ABORTED via abort), rilascia la
     * macchina da gioco, emette l'evento outbox GAME_SESSION_ABORTED e,
     * per sessioni legate a match torneo, emette anche
     * TOURNAMENT_MATCH_COMPLETED con stato ABANDONED e calcola il
     * vincitore per walkover.
     *
     * @param session        la sessione da abortire
     * @param stopReason     la ragione dello stop (es. TIMEOUT, ABORTED)
     * @param stopReasonCode il codice testuale per il payload outbox
     * @throws Exception in caso di qualsiasi errore (propaga il rollback)
     */
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
                OutboxEventStatus.PENDING.name(),
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
                        TournamentMatchStatus.ABANDONED.name()
                );
                String tournamentPayloadJson = objectMapper.writeValueAsString(tournamentDto);
                OutboxEvent tournamentOutboxEvent = new OutboxEvent(
                        UUID.randomUUID().toString(),
                        com.gameplatform.shared.domain.events.TournamentMatchCompletedEvent.EVENT_TYPE,
                        tournamentPayloadJson,
                        OutboxEventStatus.PENDING.name(),
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
