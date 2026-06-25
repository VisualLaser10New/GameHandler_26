package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.exception.SessionAlreadyActiveException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.in.EndGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.PauseGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.ResumeGameSessionUseCase;
import com.gameplatform.local.domain.ports.in.StartGameSessionUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.*;
import com.gameplatform.shared.domain.result.GameResult;
import com.gameplatform.shared.mqtt.MqttTopics;
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
public class GameSessionService implements StartGameSessionUseCase, EndGameSessionUseCase, PauseGameSessionUseCase, ResumeGameSessionUseCase {

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final ReservationRepository reservationRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public GameSessionService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            ReservationRepository reservationRepository,
            Clock clock,
            ObjectMapper objectMapper) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public GameSession start(GameId gameId, GameType gameType, List<UserId> participants, ReservationId reservationId) {
        // Check for active session on this game machine
        Optional<GameSession> activeSession = gameSessionRepository.findActiveByGameId(gameId);
        if (activeSession.isPresent()) {
            throw new SessionAlreadyActiveException("A session is already active on game machine: " + gameId.id());
        }

        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        // Validate and confirm reservation if provided
        if (reservationId != null) {
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId.value()));
            if (reservation.getStatus() == ReservationStatus.EXPIRED || Instant.now(clock).isAfter(reservation.getEndTime())) {
                throw new ReservationExpiredException("Reservation has expired: " + reservationId.value());
            }
            reservation.confirm();
            reservationRepository.save(reservation);
        }

        // Change machine state to IN_USE
        game.startUse();
        gameRepository.save(game);

        GameSessionId sessionId = new GameSessionId(UUID.randomUUID().toString());
        GameSession session = new GameSession(
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
                participants
        );

        GameSession savedSession = gameSessionRepository.save(session);

        // Publish new game machine status to MQTT
        publishGameStatePort.publishState(gameId, game.getStatus());

        // Publish session start event to MQTT
        String startTopic = MqttTopics.sessionStart(game.getBuildingId().id(), gameId.id());
        publishGameStatePort.publishSessionEvent(startTopic, savedSession);

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

        // Only release the game machine and publish status if the session was not already aborted 
        // (since aborted sessions have already released the machine)
        if (!wasAborted) {
            game.release();
            gameRepository.save(game);
            publishGameStatePort.publishState(game.getId(), game.getStatus());
        }

        // Publish session end event to MQTT
        String endTopic = MqttTopics.sessionEnd(game.getBuildingId().id(), game.getId().id());
        publishGameStatePort.publishSessionEvent(endTopic, session);

        // Generate Outbox Event
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OutboxEvent payload for GAME_SESSION_COMPLETED", e);
        }
    }

    @Override
    public void pause(GameSessionId sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        session.pause();
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Publish session pause event to MQTT
        String pauseTopic = MqttTopics.sessionPause(game.getBuildingId().id(), game.getId().id());
        publishGameStatePort.publishSessionEvent(pauseTopic, session);
    }

    @Override
    public void resume(GameSessionId sessionId) {
        GameSession session = gameSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Game session not found: " + sessionId.value()));

        session.resume();
        gameSessionRepository.save(session);

        Game game = gameRepository.findById(session.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + session.getGameId().id()));

        // Publish session resume event to MQTT
        String resumeTopic = MqttTopics.sessionResume(game.getBuildingId().id(), game.getId().id());
        publishGameStatePort.publishSessionEvent(resumeTopic, session);
    }
}
