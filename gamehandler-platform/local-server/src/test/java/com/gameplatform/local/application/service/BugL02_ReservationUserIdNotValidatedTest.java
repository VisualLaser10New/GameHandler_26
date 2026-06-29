package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.ReservationUserMismatchException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.*;
import com.gameplatform.shared.domain.model.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug L-02: GameSessionService.start() does not validate that the reservation's userId
 * matches any of the participants. Any user can hijack someone else's reservation.
 *
 * <p>The code at lines 80-94 validates gameId match and reservation status, but
 * NEVER checks {@code reservation.getUserId()} against {@code participants}.</p>
 */
class BugL02_ReservationUserIdNotValidatedTest {

    @Mock private GameSessionRepository gameSessionRepository;
    @Mock private GameRepository gameRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PublishGameStatePort publishGameStatePort;
    @Mock private ReservationRepository reservationRepository;

    private Clock clock;
    private ObjectMapper objectMapper;
    private GameSessionService gameSessionService;

    private static final Instant FIXED_NOW = Instant.parse("2026-06-29T08:00:00Z");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("building-1");
    private static final UserId USER_A = new UserId("user-A");
    private static final UserId USER_B = new UserId("user-B");
    private static final ReservationId RESERVATION_ID = new ReservationId("reservation-1");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clock = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));
        objectMapper = new ObjectMapper();
        gameSessionService = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper
        );
    }

    @Test
    @DisplayName("BUG L-02: User-B cannot start a session using User-A's reservation because ReservationUserMismatchException is thrown")
    void anyUserCanHijackAnotherUsersReservation() {
        // -- Reservation belongs to User-A
        Reservation reservationForUserA = new Reservation(
                RESERVATION_ID, GAME_ID, USER_A, ReservationStatus.PENDING,
                FIXED_NOW.minusSeconds(300),   // started 5 min ago
                FIXED_NOW.plusSeconds(3600),    // ends in 1 hour
                FIXED_NOW.minusSeconds(600)     // created 10 min ago
        );

        // -- Game machine is RESERVED (for User-A)
        Game game = new Game(GAME_ID, GameType.CHESS, "Chess Table 1", BUILDING_ID, GameMachineStatus.RESERVED);

        // -- Mock setup
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(game));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(reservationForUserA));
        when(reservationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // -- User-B starts a session using User-A's reservation
        assertThrows(ReservationUserMismatchException.class, () -> {
            gameSessionService.start(
                    GAME_ID,
                    GameType.CHESS,
                    List.of(USER_B),  // User-B is the participant, NOT User-A
                    RESERVATION_ID    // Using User-A's reservation
            );
        });

        // Verify the reservation was NOT confirmed
        assertNotEquals(ReservationStatus.CONFIRMED, reservationForUserA.getStatus());
        verify(gameSessionRepository, never()).save(any());
    }
}
