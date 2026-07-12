package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Bug L-08: the StartGameSessionUseCase contract accepts reservation status IN
 * (PENDING, CONFIRMED). The current service treats CONFIRMED as already used.
 */
@ExtendWith(MockitoExtension.class)
class BugL08_ConfirmedReservationStartSessionTest {

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;

    private GameSessionService service;

    private static final Instant NOW = Instant.parse("2026-06-29T08:00:00Z");
    private static final GameId GAME_ID = new GameId("game-1");
    private static final BuildingId BUILDING_ID = new BuildingId("building-1");
    private static final UserId USER_ID = new UserId("user-1");
    private static final ReservationId RESERVATION_ID = new ReservationId("reservation-1");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneId.of("UTC"));
        service = new GameSessionService(
                gameSessionRepository,
                gameRepository,
                outboxEventRepository,
                publishGameStatePort,
                reservationRepository,
                clock,
                new ObjectMapper(),
                gameDefinitionLocalRepository
        );
    }

    @Test
    @DisplayName("BUG L-08: a valid CONFIRMED reservation should start a session instead of being rejected as already used")
    void confirmedReservationShouldStartSession() {
        Reservation confirmedReservation = new Reservation(
                RESERVATION_ID,
                GAME_ID,
                USER_ID,
                ReservationStatus.CONFIRMED,
                NOW.minusSeconds(60),
                NOW.plusSeconds(3600),
                NOW.minusSeconds(7200)
        );
        Game reservedGame = new Game(GAME_ID, GameType.CHESS, "Chess Table", BUILDING_ID, GameMachineStatus.RESERVED);

        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(reservedGame));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(confirmedReservation));
        when(gameRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameSessionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameDefinitionLocalRepository.findByGameType(any())).thenReturn(Optional.empty());

        GameSession session = assertDoesNotThrow(() -> service.start(
                GAME_ID,
                GameType.CHESS,
                List.of(USER_ID, new UserId("opponent")),
                RESERVATION_ID
        ));

        assertNotNull(session);
        assertEquals(GameStatus.IN_PROGRESS, session.getStatus());
        assertEquals(GameMachineStatus.IN_USE, reservedGame.getStatus());
    }
}
