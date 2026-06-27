package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.exception.ReservationExpiredException;
import com.gameplatform.local.domain.exception.ReservationNotFoundException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.in.CancelReservationUseCase;
import com.gameplatform.local.domain.ports.in.CreateReservationUseCase;
import com.gameplatform.local.domain.ports.in.GetReservationsUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class ReservationService implements CreateReservationUseCase, CancelReservationUseCase, GetReservationsUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;
    private final GameRepository gameRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PublishGameStatePort publishGameStatePort;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ReservationService(
            ReservationRepository reservationRepository,
            GameRepository gameRepository,
            OutboxEventRepository outboxEventRepository,
            PublishGameStatePort publishGameStatePort,
            Clock clock,
            ObjectMapper objectMapper) {
        this.reservationRepository = reservationRepository;
        this.gameRepository = gameRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.publishGameStatePort = publishGameStatePort;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public Reservation create(GameId gameId, UserId userId, Instant start, Instant end) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + gameId.id()));

        if (end.isBefore(Instant.now(clock))) {
            throw new ReservationExpiredException("Cannot create a reservation in the past");
        }

        // Changes state to RESERVED, throws InvalidGameStateTransitionException if not AVAILABLE
        game.reserve();
        gameRepository.save(game);

        ReservationId reservationId = new ReservationId(UUID.randomUUID().toString());
        Reservation reservation = new Reservation(
                reservationId,
                gameId,
                userId,
                ReservationStatus.PENDING,
                start,
                end,
                Instant.now(clock)
        );

        Reservation savedReservation = reservationRepository.save(reservation);

        // Generate Outbox Event
        try {
            Map<String, Object> payload = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "occurredAt", Instant.now(clock).toString(),
                    "reservationId", savedReservation.getId().value(),
                    "gameId", gameId.id(),
                    "userId", userId.value(),
                    "buildingId", game.getBuildingId().id(),
                    "gameType", game.getGameType().name()
            );
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID().toString(),
                    "RESERVATION_CREATED",
                    payloadJson,
                    "PENDING",
                    Instant.now(clock),
                    null,
                    0
            );
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OutboxEvent payload for RESERVATION_CREATED", e);
        }

        // Publish new game machine status to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishGameStatePort.publishState(gameId, game.getStatus());
                        } catch (Exception e) {
                            log.warn("Failed to publish game state to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            publishGameStatePort.publishState(gameId, game.getStatus());
        }

        return savedReservation;
    }

    @Override
    public void cancel(ReservationId reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation not found: " + reservationId.value()));

        if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new ReservationExpiredException("Cannot cancel an expired reservation: " + reservationId.value());
        }

        if (!reservation.canBeCancelled(clock)) {
            throw new IllegalStateException("Reservation cannot be cancelled because it is not pending or start time is within 1 hour");
        }

        reservation.cancel();
        reservationRepository.save(reservation);

        Game game = gameRepository.findById(reservation.getGameId())
                .orElseThrow(() -> new GameNotAvailableException("Game machine not found: " + reservation.getGameId().id()));

        game.release();
        gameRepository.save(game);

        // Generate Outbox Event
        try {
            Map<String, Object> payload = Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "occurredAt", Instant.now(clock).toString(),
                    "reservationId", reservation.getId().value(),
                    "gameId", game.getId().id(),
                    "userId", reservation.getUserId().value(),
                    "buildingId", game.getBuildingId().id(),
                    "gameType", game.getGameType().name()
            );
            String payloadJson = objectMapper.writeValueAsString(payload);

            OutboxEvent outboxEvent = new OutboxEvent(
                    UUID.randomUUID().toString(),
                    "RESERVATION_CANCELLED",
                    payloadJson,
                    "PENDING",
                    Instant.now(clock),
                    null,
                    0
            );
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize OutboxEvent payload for RESERVATION_CANCELLED", e);
        }

        // Publish new game machine status to MQTT
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            publishGameStatePort.publishState(game.getId(), game.getStatus());
                        } catch (Exception e) {
                            log.warn("Failed to publish game state to MQTT after transaction commit", e);
                        }
                    }
                }
            );
        } else {
            publishGameStatePort.publishState(game.getId(), game.getStatus());
        }
    }

    @Override
    public List<Reservation> getByUser(UserId userId) {
        return reservationRepository.findByUserId(userId);
    }

    @Override
    public List<Reservation> getByGame(GameId gameId) {
        return reservationRepository.findByGameId(gameId);
    }
}
