package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.exception.InvalidGameStateTransitionException;
import com.gameplatform.local.domain.exception.ReservationAlreadyUsedException;
import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.exception.ReservationUserMismatchException;
import com.gameplatform.local.domain.exception.SessionAlreadyActiveException;
import com.gameplatform.local.domain.exception.TournamentMatchNotFoundException;
import com.gameplatform.local.domain.exception.TournamentMatchNotScheduledException;
import com.gameplatform.local.domain.exception.TournamentMatchValidationException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.CreateLobbyUseCase;
import com.gameplatform.local.domain.ports.in.JoinLobbyUseCase;
import com.gameplatform.local.domain.ports.in.StartLobbyUseCase;
import com.gameplatform.local.domain.ports.in.CancelLobbyUseCase;
import com.gameplatform.local.domain.ports.in.GetActiveLobbyUseCase;
import com.gameplatform.shared.domain.game.GameFactory;
import com.gameplatform.shared.domain.game.GameLifecycle;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.dto.TournamentMatchResultDto;
import com.gameplatform.shared.mqtt.MqttTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class GameSessionService implements StartGameSessionUseCase, EndGameSessionUseCase, PauseGameSessionUseCase, ResumeGameSessionUseCase, CreateLobbyUseCase, JoinLobbyUseCase, StartLobbyUseCase, CancelLobbyUseCase, GetActiveLobbyUseCase {

    private static final Logger log = LoggerFactory.getLogger(GameSessionService.class);

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;
    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final String buildingId;

    public GameSessionService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            ReservationRepository reservationRepository,
            Clock clock,
            ObjectMapper objectMapper,
            GameDefinitionLocalRepository gameDefinitionLocalRepository,
            TournamentMatchLocalRepository tournamentMatchLocalRepository,
            @Value("${app.building-id}") String buildingId) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
        this.buildingId = buildingId;
    }

    @Override
    public GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId) {
        return start(gameId, gameType, participants, reservationId, null);
    }

    /**
     * FASE 6 tournament-aware start. When {@code tournamentMatchId != null}:
     * load the {@link TournamentMatchLocal} via
     * {@code tournamentMatchLocalRepository.findById}; validate status==SCHEDULED
     * (else {@link TournamentMatchNotScheduledException});
     * NO building validation on Local (ambiguity O — the central push only
     * sends to the involved building, so receiving the match implies it belongs
     * here); validate the requester is among participants (participantA ==
     * userId OR participantB == userId) for INDIVIDUAL matches — for team
     * matches skip participant check (ambiguity F); load {@link GameDefinitionLocal},
     * validate {@code team_allowed} against the match (if def.teamAllowed()
     * doesn't match the tournament's teamBased expectation →
     * {@link TournamentMatchValidationException});
     * update the {@link TournamentMatchLocal} status to IN_PROGRESS via
     * {@code withStatus(...)} + save; pass {@code tournamentMatchId} +
     * {@code tournamentId} (resolved from the local match) to the
     * {@link GameSession} constructor. Reuses the existing reservation +
     * machine-state + min/max validation.
     */
    public GameSession start(GameId gameId, GameType gameType, List<UserId> participants,
                             ReservationId reservationId, TournamentMatchId tournamentMatchId) {
        List<UserId> activeParticipants = participants != null ? participants : List.of();

        // FASE 6 — tournament match binding validation (only when tournamentMatchId != null)
        TournamentId resolvedTournamentId = null;
        if (tournamentMatchId != null) {
            TournamentMatchLocal localMatch = tournamentMatchLocalRepository.findById(tournamentMatchId)
                    .orElseThrow(() -> new TournamentMatchNotFoundException(
                            "Tournament match not found: " + tournamentMatchId.value()));
            if (localMatch.getStatus() != TournamentMatchStatus.SCHEDULED) {
                throw new TournamentMatchNotScheduledException(
                        "Tournament match " + tournamentMatchId.value()
                                + " is not SCHEDULED (current: " + localMatch.getStatus() + ")");
            }
            // team_allowed: infer the match's team-based nature from the replicated
            // game_definitions_local (ambiguity O — no team_based column in
            // tournament_matches_local). If the definition is missing, fall back to
            // the in-memory GameFactory team-allowed flag below is not available, so
            // we just trust the participant count as supplied.
            Optional<GameDefinitionLocal> tournamentDef = gameDefinitionLocalRepository.findByGameType(gameType);
            if (tournamentDef.isPresent()) {
                GameDefinitionLocal def = tournamentDef.get();
                boolean teamBased = def.isTeamAllowed();
                if (teamBased) {
                    // team match: the session carries a single pseudo-participant (team_id).
                    if (activeParticipants.size() != 1) {
                        throw new TournamentMatchValidationException(
                                "Team tournament match " + tournamentMatchId.value()
                                        + " expects exactly 1 pseudo-participant (team_id), got "
                                        + activeParticipants.size());
                    }
                    // requester validation skipped for team (ambiguity F — the user is not a
                    // direct participant of the match; the pseudo-participant is the team_id).
                } else {
                    // individual match: two user participants.
                    if (activeParticipants.size() != 2) {
                        throw new TournamentMatchValidationException(
                                "Individual tournament match " + tournamentMatchId.value()
                                        + " expects exactly 2 participants, got " + activeParticipants.size());
                    }
                    // validate that the participants are exactly the match's
                    // participantA / participantB (the requester is among the match
                    // participants — defensive check against a caller passing arbitrary users).
                    String pa = localMatch.getParticipantA();
                    String pb = localMatch.getParticipantB();
                    for (UserId p : activeParticipants) {
                        boolean isMatchParticipant =
                                (pa != null && pa.equals(p.value())) || (pb != null && pb.equals(p.value()));
                        if (!isMatchParticipant) {
                            throw new TournamentMatchValidationException(
                                    "Tournament match " + tournamentMatchId.value()
                                            + " participant " + p.value()
                                            + " is not among the match participants ("
                                            + pa + ", " + pb + ")");
                        }
                    }
                }
            }
            // Update the local match to IN_PROGRESS.
            tournamentMatchLocalRepository.save(localMatch.withStatus(TournamentMatchStatus.IN_PROGRESS));
            resolvedTournamentId = localMatch.getTournamentId();
        }

        // Check for active session on this game machine
        Optional<GameSession> activeSession = gameSessionRepository.findActiveByGameId(gameId);
        if (activeSession.isPresent()) {
            throw new SessionAlreadyActiveException("A session is already active on game machine: " + gameId.id());
        }

        Game game = gameRepository.findByIdForUpdate(gameId)
                .or(() -> gameRepository.findById(gameId))
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        // Validate and confirm reservation if provided
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId.value()));
            if (!reservation.getGameId().equals(gameId)) {
                throw new InvalidGameStateTransitionException("Reservation game machine does not match the requested game machine");
            }
            if (!activeParticipants.contains(reservation.getUserId())) {
                throw new ReservationUserMismatchException("Reservation user does not match the participants list");
            }
            if (reservation.getStatus() == ReservationStatus.CANCELLED) {
                throw new ReservationExpiredException("Reservation has been cancelled: " + reservationId.value());
            }
            if (reservation.getStatus() == ReservationStatus.EXPIRED || Instant.now(clock).isAfter(reservation.getEndTime())) {
                throw new ReservationExpiredException("Reservation has expired: " + reservationId.value());
            }
            if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.confirm();
                reservationRepository.save(reservation);
            }
        } else {
            if (game.getStatus() == GameMachineStatus.RESERVED || game.getStatus() == GameMachineStatus.LOBBY) {
                throw new GameNotAvailableException("Game machine is reserved or in lobby");
            }
        }

        // FASE 2: validate participants against the replicated game_definitions_local,
        // falling back to in-memory GameFactory for offline-first resilience.
        Optional<GameDefinitionLocal> localDef = gameDefinitionLocalRepository.findByGameType(gameType);
        int min;
        int max;
        if (localDef.isPresent()) {
            GameDefinitionLocal def = localDef.get();
            min = def.getMinPlayers();
            max = def.getMaxPlayers();
            // team_allowed: enforced at the tournament/registration context (FASE 6).
        } else {
            GameLifecycle gameLogic = GameFactory.createGame(gameType, null);
            min = gameLogic.getMinPlayers();
            max = gameLogic.getMaxPlayers();
        }
        if (activeParticipants.size() < min || activeParticipants.size() > max) {
            throw new IllegalArgumentException(
                    "Number of players for " + gameType + " must be between " + min + " and " + max
                    + " (got " + activeParticipants.size() + ")");
        }

        // Change machine state to IN_USE
        game.startUse();
        gameRepository.save(game);

        GameSessionId sessionId = new GameSessionId(UUID.randomUUID().toString());
        GameSession session;
        if (tournamentMatchId != null) {
            session = new GameSession(
                    sessionId,
                    gameId,
                    gameType,
                    game.getBuildingId(),
                    GameStatus.IN_PROGRESS,
                    Instant.now(clock),
                    null,
                    null,
                    null,
                    null,
                    null,
                    activeParticipants,
                    tournamentMatchId,
                    resolvedTournamentId
            );
        } else {
            session = new GameSession(
                    sessionId,
                    gameId,
                    gameType,
                    game.getBuildingId(),
                    GameStatus.IN_PROGRESS,
                    Instant.now(clock),
                    null,
                    null,
                    null,
                    null,
                    null,
                    activeParticipants
            );
        }

        GameSession savedSession = gameSessionRepository.save(session);

        // Publish new game machine status and session start event to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishGameStatePort.publishState(gameId, game.getStatus());
                            String startTopic = MqttTopics.sessionStart(game.getBuildingId().id(), gameId.id());
                            publishGameStatePort.publishSessionEvent(startTopic, savedSession);
                        } catch (Exception e) {
                            log.warn("Failed to publish game state or session start event to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            publishGameStatePort.publishState(gameId, game.getStatus());
            String startTopic = MqttTopics.sessionStart(game.getBuildingId().id(), gameId.id());
            publishGameStatePort.publishSessionEvent(startTopic, savedSession);
        }

        return savedSession;
    }

    @Override
    public void end(GameSessionId sessionId, GameResult result) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        // Late arrival handling: accept ending an ABORTED session to record final result
        if (session.getStatus() == GameStatus.COMPLETED) {
            return; // Already completed
        }

        boolean wasAborted = session.getStatus() == GameStatus.ABORTED;

        // Transition session to COMPLETED status
        session.complete(result, Instant.now(clock));
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Only release the game machine if the session was not already aborted 
        // (since aborted sessions have already released the machine)
        if (!wasAborted) {
            game.release();
            gameRepository.save(game);
        }

        // Publish session end event and game status to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            if (!wasAborted) {
                                publishGameStatePort.publishState(game.getId(), game.getStatus());
                            }
                            String endTopic = MqttTopics.sessionEnd(game.getBuildingId().id(), game.getId().id());
                            publishGameStatePort.publishSessionEvent(endTopic, session);
                        } catch (Exception e) {
                            log.warn("Failed to publish game state or session end event to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            if (!wasAborted) {
                publishGameStatePort.publishState(game.getId(), game.getStatus());
            }
            String endTopic = MqttTopics.sessionEnd(game.getBuildingId().id(), game.getId().id());
            publishGameStatePort.publishSessionEvent(endTopic, session);
        }

        // Generate Outbox Event
        // S1: only emit GAME_SESSION_COMPLETED when the session was not already aborted.
        // An aborted-then-ended session has already produced its central-stats event
        // (GAME_SESSION_ABORTED) at abort time; emitting COMPLETED here would double-count
        // the session in central aggregated_statistics (totalSessions / avgDurationSeconds).
        if (!wasAborted) {
            try {
                String resultJsonString = null;
                if (result != null) {
                    resultJsonString = objectMapper.writeValueAsString(result);
                }

                Map<String, Object> payload = new java.util.HashMap<>();
                payload.put("eventId", UUID.randomUUID().toString());
                payload.put("occurredAt", Instant.now(clock).toString());
                payload.put("sessionId", session.getId().value());
                payload.put("gameType", session.getGameType().name());
                payload.put("durationSeconds", session.getDurationSeconds());
                payload.put("status", session.getStatus().name());
                // FASE 3 — enriched GAME_SESSION_COMPLETED payload (PIANO §2.2):
                // participants + winnerId + winCondition are emitted explicitly
                // (alongside the existing resultJson) so the Central
                // SyncEventProcessor can populate the player_match_facts /
                // player_statistics read-models without re-parsing the polymorphic
                // GameResult JSON. Idempotent backward-compat: older Central code
                // that ignores these fields is unaffected (the payload stays a JSON
                // String; OutboxEventDto/SyncPayloadDto shapes are unchanged).
                payload.put("participants", session.getParticipants().stream()
                        .map(UserId::value).toList());
                payload.put("winnerId", session.getWinnerId() != null
                        ? session.getWinnerId().value() : null);
                payload.put("winCondition", session.getWinCondition() != null
                        ? session.getWinCondition().name() : null);
                if (resultJsonString != null) {
                    payload.put("resultJson", resultJsonString);
                }
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

                // FASE 6 — when the session is bound to a tournament match, emit a
                // second outbox row TOURNAMENT_MATCH_COMPLETED (atomic in this tx)
                // and flip the local match row to COMPLETED. The payload is a
                // serialised TournamentMatchResultDto record (per Q5 — NOT a Map)
                // so the central SyncEventProcessor can readValue it back.
                if (session.getTournamentMatchId() != null) {
                    String winner = session.getWinnerId() != null ? session.getWinnerId().value() : null;
                    String resultData = result != null ? objectMapper.writeValueAsString(result) : null;
                    TournamentMatchResultDto tournamentDto = new TournamentMatchResultDto(
                            session.getTournamentMatchId().value(),
                            winner,
                            resultData,
                            "COMPLETED"
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

                    // Flip the local match to COMPLETED.
                    TournamentMatchLocal localMatch = tournamentMatchLocalRepository
                            .findById(session.getTournamentMatchId())
                            .orElse(null);
                    if (localMatch != null) {
                        tournamentMatchLocalRepository.save(localMatch.withStatus(TournamentMatchStatus.COMPLETED));
                    } else {
                        log.warn("Tournament match {} not found locally while completing session {}",
                                session.getTournamentMatchId().value(), session.getId().value());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to serialize OutboxEvent payload for GAME_SESSION_COMPLETED", e);
            }
        }
    }

    @Override
    public void pause(GameSessionId sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        session.pause(Instant.now(clock));
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Publish session pause event to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            String pauseTopic = MqttTopics.sessionPause(game.getBuildingId().id(), game.getId().id());
                            publishGameStatePort.publishSessionEvent(pauseTopic, session);
                        } catch (Exception e) {
                            log.warn("Failed to publish session pause event to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            String pauseTopic = MqttTopics.sessionPause(game.getBuildingId().id(), game.getId().id());
            publishGameStatePort.publishSessionEvent(pauseTopic, session);
        }
    }

    @Override
    public void resume(GameSessionId sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        session.resume(Instant.now(clock));
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Publish session resume event to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            String resumeTopic = MqttTopics.sessionResume(game.getBuildingId().id(), game.getId().id());
                            publishGameStatePort.publishSessionEvent(resumeTopic, session);
                        } catch (Exception e) {
                            log.warn("Failed to publish session resume event to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            String resumeTopic = MqttTopics.sessionResume(game.getBuildingId().id(), game.getId().id());
            publishGameStatePort.publishSessionEvent(resumeTopic, session);
        }
    }

    @Override
    public GameSession createLobby(GameId gameId, GameType gameType, UserId creatorId) {
        Optional<GameSession> activeSession = gameSessionRepository.findActiveByGameId(gameId);
        if (activeSession.isPresent()) {
            throw new SessionAlreadyActiveException("A session is already active on game machine: " + gameId.id());
        }

        Game game = gameRepository.findByIdForUpdate(gameId)
                .or(() -> gameRepository.findById(gameId))
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        // If the game machine is stuck in a stale LOBBY status (a previous
        // lobby session was aborted/cancelled but the game_catalog was not
        // updated back to AVAILABLE — e.g. client crash before the cancel
        // MQTT echo arrived), release it first so we can create a new
        // lobby.  This is safe because we already verified above that no
        // active session (WAITING/IN_PROGRESS/PAUSED) exists for this game.
        if (game.getStatus() == GameMachineStatus.LOBBY) {
            game.release();
        }

        // Set game status to LOBBY
        game.setLobby();
        gameRepository.save(game);

        GameSessionId sessionId = new GameSessionId(UUID.randomUUID().toString());
        GameSession session = new GameSession(
                sessionId,
                gameId,
                gameType,
                game.getBuildingId(),
                GameStatus.WAITING,
                Instant.now(clock),
                null,
                null,
                null,
                null,
                null,
                List.of(creatorId)
        );

        GameSession savedSession = gameSessionRepository.save(session);

        // Publish to MQTT
        deferMqttPublish(() -> {
            publishGameStatePort.publishState(gameId, game.getStatus());
            String topic = lobbyTopic(session, "create");
            com.gameplatform.shared.mqtt.payload.SessionStartPayload payload =
                    new com.gameplatform.shared.mqtt.payload.SessionStartPayload(
                            savedSession.getId().value(),
                            savedSession.getGameType(),
                            savedSession.getParticipants().stream().map(UserId::value).toList()
                    );
            publishGameStatePort.publishSessionEvent(topic, payload);
        });

        return savedSession;
    }

    @Override
    public GameSession joinLobby(GameSessionId sessionId, UserId userId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        if (session.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("Session is not in WAITING (lobby) state");
        }

        GameLifecycle gameLogic = GameFactory.createGame(session.getGameType(), null);
        int max = gameLogic.getMaxPlayers();

        if (session.getParticipants().size() >= max) {
            throw new IllegalStateException("Lobby is already full");
        }

        session.addParticipant(userId);
        GameSession savedSession = gameSessionRepository.save(session);

        // Publish to MQTT
        deferMqttPublish(() -> {
            String topic = lobbyTopic(session, "join");
            com.gameplatform.shared.mqtt.payload.LobbyJoinPayload payload =
                    new com.gameplatform.shared.mqtt.payload.LobbyJoinPayload(
                            savedSession.getId().value(),
                            userId.value()
                    );
            publishGameStatePort.publishSessionEvent(topic, payload);
        });

        return savedSession;
    }

    @Override
    public GameSession startLobby(GameSessionId sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        if (session.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("Session is not in WAITING (lobby) state");
        }

        GameLifecycle gameLogic = GameFactory.createGame(session.getGameType(), null);
        int min = gameLogic.getMinPlayers();

        if (session.getParticipants().size() < min) {
            throw new IllegalStateException("Not enough players to start the game");
        }

        Game game = gameRepository.findByIdForUpdate(session.getGameId())
                .or(() -> gameRepository.findById(session.getGameId()))
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Start using the game
        game.startUse();
        gameRepository.save(game);

        // Update session status to IN_PROGRESS
        session.setStatus(GameStatus.IN_PROGRESS);
        GameSession savedSession = gameSessionRepository.save(session);

        // Publish to MQTT
        deferMqttPublish(() -> {
            publishGameStatePort.publishState(game.getId(), game.getStatus());
            String topic = lobbyTopic(session, "start");
            publishGameStatePort.publishSessionEvent(topic, savedSession);
        });

        return savedSession;
    }

    @Override
    public GameSession cancelLobby(GameSessionId sessionId, UserId userId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        if (session.getStatus() != GameStatus.WAITING) {
            throw new IllegalStateException("Session is not in WAITING (lobby) state");
        }

        // Only the creator (first participant) can cancel.  We no longer
        // restrict cancellation based on participant count: if the creator
        // leaves, the lobby should be torn down regardless of whether other
        // players had joined (they may have already disconnected).
        if (session.getParticipants().isEmpty()
                || !session.getParticipants().get(0).equals(userId)) {
            throw new IllegalStateException("Only the lobby creator can cancel the lobby");
        }

        // Cancel the lobby session
        session.cancelLobby(Instant.now(clock));
        GameSession savedSession = gameSessionRepository.save(session);

        // Release the game machine back to AVAILABLE
        Game game = gameRepository.findByIdForUpdate(session.getGameId())
                .or(() -> gameRepository.findById(session.getGameId()))
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));
        game.release();
        gameRepository.save(game);

        // Publish game state change (AVAILABLE) and a lobby cancel event to MQTT
        deferMqttPublish(() -> {
            publishGameStatePort.publishState(game.getId(), game.getStatus());
            String topic = lobbyTopic(session, "cancel");
            publishGameStatePort.publishSessionEvent(topic, savedSession);
        });

        return savedSession;
    }

    private static String lobbyTopic(GameSession session, String action) {
        return "building/" + session.getBuildingId().id()
                + "/game/" + session.getGameId().id()
                + "/session/lobby/" + action;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Optional<GameSession> getActiveLobby(GameId gameId) {
        return gameSessionRepository.findActiveByGameId(gameId)
                .filter(s -> s.getStatus() == GameStatus.WAITING);
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
}
