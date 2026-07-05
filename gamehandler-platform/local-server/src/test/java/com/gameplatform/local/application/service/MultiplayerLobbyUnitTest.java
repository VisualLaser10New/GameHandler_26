package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.exception.GameNotAvailableException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.shared.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MultiplayerLobbyUnitTest {

    private static final Instant NOW = Instant.parse("2026-06-01T10:00:00Z");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;

    GameSessionService service;

    private Map<String, Game> gameDb;
    private Map<String, GameSession> sessionDb;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        gameDb = new HashMap<>();
        sessionDb = new HashMap<>();

        // Mock gameRepository.findByIdForUpdate
        lenient().when(gameRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            GameId id = invocation.getArgument(0);
            return Optional.ofNullable(gameDb.get(id.id()));
        });

        // Mock gameRepository.save
        lenient().when(gameRepository.save(any())).thenAnswer(invocation -> {
            Game g = invocation.getArgument(0);
            gameDb.put(g.getId().id(), g);
            return g;
        });

        // Mock gameSessionRepository.save
        lenient().when(gameSessionRepository.save(any())).thenAnswer(invocation -> {
            GameSession s = invocation.getArgument(0);
            sessionDb.put(s.getId().value(), s);
            return s;
        });

        // Mock gameSessionRepository.findById
        lenient().when(gameSessionRepository.findById(any())).thenAnswer(invocation -> {
            GameSessionId id = invocation.getArgument(0);
            return Optional.ofNullable(sessionDb.get(id.value()));
        });

        // Mock gameSessionRepository.findActiveByGameId
        lenient().when(gameSessionRepository.findActiveByGameId(any())).thenAnswer(invocation -> {
            GameId id = invocation.getArgument(0);
            return sessionDb.values().stream()
                    .filter(s -> s.getGameId().equals(id)
                            && (s.getStatus() == GameStatus.IN_PROGRESS
                                    || s.getStatus() == GameStatus.WAITING
                                    || s.getStatus() == GameStatus.PAUSED))
                    .findFirst();
        });

        // Mock gameSessionRepository.findByStatus
        lenient().when(gameSessionRepository.findByStatus(any())).thenAnswer(invocation -> {
            GameStatus status = invocation.getArgument(0);
            return sessionDb.values().stream()
                    .filter(s -> s.getStatus() == status)
                    .toList();
        });

        service = new GameSessionService(
                gameSessionRepository,
                gameRepository,
                null,
                publishGameStatePort,
                null,
                clock,
                new ObjectMapper()
        );
    }

    @Test
    void testMultiplayerForMultipleMatches() {
        // Setup two Foosball games
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        Game g2 = new Game(new GameId("game-2"), GameType.FOOSBALL, "Foosball 2", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);
        gameDb.put("game-2", g2);

        // Create Lobby 1
        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));
        assertNotNull(s1);
        assertEquals(GameStatus.WAITING, s1.getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-1").getStatus());

        // Create Lobby 2
        GameSession s2 = service.createLobby(g2.getId(), GameType.FOOSBALL, new UserId("user-3"));
        assertNotNull(s2);
        assertEquals(GameStatus.WAITING, s2.getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-2").getStatus());

        // Join other players
        service.joinLobby(s1.getId(), new UserId("user-2"));
        service.joinLobby(s2.getId(), new UserId("user-4"));

        // Start both lobbies
        service.startLobby(s1.getId());
        service.startLobby(s2.getId());

        // Verify status
        assertEquals(GameStatus.IN_PROGRESS, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameStatus.IN_PROGRESS, sessionDb.get(s2.getId().value()).getStatus());
        assertEquals(GameMachineStatus.IN_USE, gameDb.get("game-1").getStatus());
        assertEquals(GameMachineStatus.IN_USE, gameDb.get("game-2").getStatus());

        assertEquals(2, sessionDb.get(s1.getId().value()).getParticipants().size());
        assertEquals(2, sessionDb.get(s2.getId().value()).getParticipants().size());
    }

    @Test
    void testMultiplayerForSingleMatch_LimitsAndConstraints() {
        // Setup a Foosball game (limits 2-4)
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));

        // 1 player joined. Trying to start should fail (Foosball min is 2)
        assertThrows(IllegalStateException.class, () -> service.startLobby(s1.getId()));

        // Join up to 4 players (max)
        service.joinLobby(s1.getId(), new UserId("user-2"));
        service.joinLobby(s1.getId(), new UserId("user-3"));
        service.joinLobby(s1.getId(), new UserId("user-4"));

        assertEquals(4, sessionDb.get(s1.getId().value()).getParticipants().size());

        // Joining a 5th player should fail (max is 4)
        assertThrows(IllegalStateException.class, () -> service.joinLobby(s1.getId(), new UserId("user-5")));

        // Start lobby should now succeed
        assertDoesNotThrow(() -> service.startLobby(s1.getId()));
        assertEquals(GameStatus.IN_PROGRESS, sessionDb.get(s1.getId().value()).getStatus());
    }

    @Test
    void testSinglePlayerLimits_SlotMachine() {
        // Slot machine limits: 1-1
        Game g = new Game(new GameId("slot-1"), GameType.SLOT_MACHINE, "Slot 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("slot-1", g);

        GameSession s = service.createLobby(g.getId(), GameType.SLOT_MACHINE, new UserId("user-1"));

        // Trying to join a second player should fail (max is 1)
        assertThrows(IllegalStateException.class, () -> service.joinLobby(s.getId(), new UserId("user-2")));

        // Start lobby succeeds with 1 player
        assertDoesNotThrow(() -> service.startLobby(s.getId()));
    }

    @Test
    void testJoinDifferentPlayersToSameMatch() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        GameSession s = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s.getId(), new UserId("user-2"));
        service.joinLobby(s.getId(), new UserId("user-3"));

        GameSession updated = sessionDb.get(s.getId().value());
        assertTrue(updated.getParticipants().contains(new UserId("user-1")));
        assertTrue(updated.getParticipants().contains(new UserId("user-2")));
        assertTrue(updated.getParticipants().contains(new UserId("user-3")));
        assertEquals(3, updated.getParticipants().size());
    }

    @Test
    void testJoinDifferentPlayersToDifferentMatches() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        Game g2 = new Game(new GameId("game-2"), GameType.FOOSBALL, "Foosball 2", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);
        gameDb.put("game-2", g2);

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));
        GameSession s2 = service.createLobby(g2.getId(), GameType.FOOSBALL, new UserId("user-2"));

        service.joinLobby(s1.getId(), new UserId("user-3"));
        service.joinLobby(s2.getId(), new UserId("user-4"));

        GameSession updated1 = sessionDb.get(s1.getId().value());
        GameSession updated2 = sessionDb.get(s2.getId().value());

        assertTrue(updated1.getParticipants().contains(new UserId("user-1")));
        assertTrue(updated1.getParticipants().contains(new UserId("user-3")));
        assertFalse(updated1.getParticipants().contains(new UserId("user-4")));

        assertTrue(updated2.getParticipants().contains(new UserId("user-2")));
        assertTrue(updated2.getParticipants().contains(new UserId("user-4")));
        assertFalse(updated2.getParticipants().contains(new UserId("user-3")));
    }

    @Test
    void testLobbyExpirationAfter10Minutes() {
        // Setup Foosball game
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        // Create Lobby
        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));

        // Before 10 minutes (e.g. 5 minutes later)
        Instant fiveMinLater = NOW.plus(5, java.time.temporal.ChronoUnit.MINUTES);
        Clock clockFiveMin = mock(Clock.class);
        when(clockFiveMin.instant()).thenReturn(fiveMinLater);
        
        LobbyExpirationService expirationService5 = new LobbyExpirationService(
                gameSessionRepository,
                gameRepository,
                publishGameStatePort,
                clockFiveMin
        );
        expirationService5.expireLobbies();

        // Verify lobby still WAITING and game still LOBBY
        assertEquals(GameStatus.WAITING, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-1").getStatus());

        // After 10 minutes (e.g. 11 minutes later)
        Instant elevenMinLater = NOW.plus(11, java.time.temporal.ChronoUnit.MINUTES);
        Clock clockElevenMin = mock(Clock.class);
        when(clockElevenMin.instant()).thenReturn(elevenMinLater);

        LobbyExpirationService expirationService11 = new LobbyExpirationService(
                gameSessionRepository,
                gameRepository,
                publishGameStatePort,
                clockElevenMin
        );
        expirationService11.expireLobbies();

        // Verify lobby aborted and game released (AVAILABLE)
        assertEquals(GameStatus.ABORTED, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, gameDb.get("game-1").getStatus());
    }

    @Test
    void testCreatorCancelLobbyWhenAloneReleasesGameMachine() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-1").getStatus());

        // Creator goes back with no other players joined -> lobby cancelled, game released
        service.cancelLobby(s1.getId(), new UserId("user-1"));

        assertEquals(GameStatus.ABORTED, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, gameDb.get("game-1").getStatus());
    }

    @Test
    void testCreatorCancelLobbyFailsWhenOthersJoined() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s1.getId(), new UserId("user-2"));

        // Creator attempts to cancel but another player has joined -> rejected, lobby stays active
        assertThrows(IllegalStateException.class, () -> service.cancelLobby(s1.getId(), new UserId("user-1")));

        assertEquals(GameStatus.WAITING, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-1").getStatus());
    }

    @Test
    void testNonCreatorCannotCancelLobby() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));

        // A user who is not the creator cannot cancel
        assertThrows(IllegalStateException.class, () -> service.cancelLobby(s1.getId(), new UserId("someone-else")));
        assertEquals(GameStatus.WAITING, sessionDb.get(s1.getId().value()).getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("game-1").getStatus());
    }

    @Test
    void testGetActiveLobbyReturnsWaitingSessionForGame() {
        Game g1 = new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1", new BuildingId("b-1"), GameMachineStatus.AVAILABLE);
        gameDb.put("game-1", g1);

        // No lobby yet -> empty
        assertTrue(service.getActiveLobby(g1.getId()).isEmpty());

        GameSession s1 = service.createLobby(g1.getId(), GameType.FOOSBALL, new UserId("user-1"));

        // Lobby created -> returned
        java.util.Optional<GameSession> active = service.getActiveLobby(g1.getId());
        assertTrue(active.isPresent());
        assertEquals(s1.getId().value(), active.get().getId().value());
        assertEquals(GameStatus.WAITING, active.get().getStatus());

        // After cancellation (ABORTED) -> not returned as active lobby anymore
        service.cancelLobby(s1.getId(), new UserId("user-1"));
        assertTrue(service.getActiveLobby(g1.getId()).isEmpty());
    }
}
