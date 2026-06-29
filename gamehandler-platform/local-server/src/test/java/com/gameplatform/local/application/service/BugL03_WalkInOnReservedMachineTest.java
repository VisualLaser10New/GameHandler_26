package com.gameplatform.local.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
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
 * Bug L-03: A walk-in session (reservationId=null) can start on a RESERVED machine.
 *
 * <p>When {@code reservationId} is null, the code skips reservation validation entirely
 * and directly calls {@code game.startUse()}. Since {@code Game.startUse()} accepts
 * both {@code AVAILABLE} and {@code RESERVED} states, a walk-in user can steal
 * a machine that was reserved by someone else.</p>
 */
class BugL03_WalkInOnReservedMachineTest {

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
    private static final UserId USER_B = new UserId("user-B");

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
    @DisplayName("BUG L-03: Walk-in user (no reservation) cannot start a session on a RESERVED machine, throwing GameNotAvailableException")
    void walkInUserCanStartSessionOnReservedMachine() {
        // -- Game machine is RESERVED for User-A (reservation exists elsewhere)
        Game reservedGame = new Game(GAME_ID, GameType.FOOSBALL, "Foosball Table 1", BUILDING_ID, GameMachineStatus.RESERVED);

        // -- Mock setup
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(reservedGame));
        when(gameRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // -- User-B starts a walk-in session (no reservationId) on the RESERVED machine
        assertThrows(GameNotAvailableException.class, () -> {
            gameSessionService.start(
                    GAME_ID,
                    GameType.FOOSBALL,
                    List.of(USER_B),
                    null  // No reservation — walk-in
            );
        });

        // The machine must remain RESERVED
        assertEquals(GameMachineStatus.RESERVED, reservedGame.getStatus());

        // Verify that no reservation validation occurred
        verify(reservationRepository, never()).findById(any());
    }
}
