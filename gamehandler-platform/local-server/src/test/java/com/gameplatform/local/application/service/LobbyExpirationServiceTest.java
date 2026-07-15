package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for Bug #2: {@link LobbyExpirationService} aborted expired
 * lobbies and released the game machine (publishing the AVAILABLE game
 * <em>state</em>) but did NOT publish a {@code lobby/cancel} session event.
 * Joiners sitting in the {@code LobbyView} therefore never received the
 * cancel signal and remained stuck on the lobby screen when the timer fired.
 * These tests prove the timer now publishes {@code lobby/cancel} (mirroring
 * the user-initiated {@code GameSessionService.cancelLobby} path) and that
 * the configurable threshold ({@code app.lobby.expiration-minutes}, default 2)
 * is honoured.
 *
 * <p>Pure-Mockito slice, no Spring context —
 * {@code TransactionSynchronizationManager.isActualTransactionActive()} is
 * false, so the non-transactional publish branch executes synchronously,
 * making the assertions deterministic.
 */
@ExtendWith(MockitoExtension.class)
class LobbyExpirationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");
    private static final String BUILDING_ID = "building-1";
    private static final GameId GAME_ID = new GameId("game-1");
    private static final GameSessionId SESSION_ID = new GameSessionId("sess-1");
    private static final UserId CREATOR = new UserId("creator-1");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;

    private LobbyExpirationService service;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        // Default 2-minute threshold, mirroring the production default.
        service = new LobbyExpirationService(
                gameSessionRepository, gameRepository, publishGameStatePort, clock, 2L);
    }

    private GameSession waitingLobby(Instant startedAt) {
        return new GameSession(
                SESSION_ID, GAME_ID, GameType.FOOSBALL, new BuildingId(BUILDING_ID),
                GameStatus.WAITING, startedAt, null, null, null, null, null,
                List.of(CREATOR));
    }

    private Game lobbyGame() {
        return new Game(GAME_ID, GameType.FOOSBALL, "Foosball 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.LOBBY);
    }

    @Test
    void expireLobbies_publishesLobbyCancelEventForExpiredLobby() {
        // Session started 3 minutes ago — beyond the 2-minute threshold.
        GameSession session = waitingLobby(NOW.minus(3, java.time.temporal.ChronoUnit.MINUTES));
        Game game = lobbyGame();

        when(clock.instant()).thenReturn(NOW);
        when(gameSessionRepository.findByStatus(GameStatus.WAITING)).thenReturn(List.of(session));
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(game));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.expireLobbies();

        // The session was aborted and the game machine released.
        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, game.getStatus());

        // Bug #2 assertion: the AVAILABLE game state is published.
        verify(publishGameStatePort).publishState(GAME_ID, GameMachineStatus.AVAILABLE);

        // Bug #2 assertion: a lobby/cancel session event is published on the
        // exact topic a joiner's LobbyView case "cancel" is subscribed to,
        // mirroring GameSessionService.cancelLobby so joiners auto-navigate.
        String cancelTopic = "building/" + session.getBuildingId().id()
                + "/game/" + session.getGameId().id()
                + "/session/lobby/cancel";
        verify(publishGameStatePort).publishSessionEvent(eq(cancelTopic), same(session));
    }

    @Test
    void expireLobbies_doesNotExpireLobbyUnderThreshold() {
        // Session started 1 minute ago — under the 2-minute threshold.
        GameSession session = waitingLobby(NOW.minus(1, java.time.temporal.ChronoUnit.MINUTES));

        when(clock.instant()).thenReturn(NOW);
        when(gameSessionRepository.findByStatus(GameStatus.WAITING)).thenReturn(List.of(session));

        service.expireLobbies();

        // Lobby unchanged: still WAITING, no publication, no game-machine touch.
        assertEquals(GameStatus.WAITING, session.getStatus());
        verifyNoInteractions(publishGameStatePort);
        verify(gameRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void expireLobbies_respectsConfigurableThreshold() {
        // A session started 30 seconds ago would survive a 2-minute threshold
        // but must expire under a sub-minute configured threshold.
        GameSession session = waitingLobby(NOW.minusSeconds(30));
        Game game = lobbyGame();
        LobbyExpirationService quickService = new LobbyExpirationService(
                gameSessionRepository, gameRepository, publishGameStatePort, clock, 0L);

        when(clock.instant()).thenReturn(NOW);
        when(gameSessionRepository.findByStatus(GameStatus.WAITING)).thenReturn(List.of(session));
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(game));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        quickService.expireLobbies();

        assertEquals(GameStatus.ABORTED, session.getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, game.getStatus());
        String cancelTopic = "building/" + session.getBuildingId().id()
                + "/game/" + session.getGameId().id()
                + "/session/lobby/cancel";
        verify(publishGameStatePort).publishState(GAME_ID, GameMachineStatus.AVAILABLE);
        verify(publishGameStatePort).publishSessionEvent(eq(cancelTopic), same(session));
    }
}
