package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.*;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for the lobby "leave" flow (root-cause fix for the
 * "joiner goes back but stays in the participants list" bug). Mirrors
 * the lobby create/join/cancel tests in {@link MultiplayerLobbyUnitTest}
 * plus the canonicalisation pattern of
 * {@code GameSessionServiceParticipantResolutionTest}.
 */
@ExtendWith(MockitoExtension.class)
class LobbyLeaveTest {

    private static final Instant NOW = Instant.parse("2026-07-15T10:00:00Z");
    private static final String BUILDING_ID = "building-1";

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock Clock clock;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock UserRepository userRepository;

    private GameSessionService service;
    private Map<String, Game> gameDb;
    private Map<String, GameSession> sessionDb;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        gameDb = new HashMap<>();
        sessionDb = new HashMap<>();

        lenient().when(gameRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            GameId id = invocation.getArgument(0);
            return Optional.ofNullable(gameDb.get(id.id()));
        });

        lenient().when(gameRepository.save(any())).thenAnswer(invocation -> {
            Game g = invocation.getArgument(0);
            gameDb.put(g.getId().id(), g);
            return g;
        });

        lenient().when(gameSessionRepository.save(any())).thenAnswer(invocation -> {
            GameSession s = invocation.getArgument(0);
            sessionDb.put(s.getId().value(), s);
            return s;
        });

        lenient().when(gameSessionRepository.findById(any())).thenAnswer(invocation -> {
            GameSessionId id = invocation.getArgument(0);
            return Optional.ofNullable(sessionDb.get(id.value()));
        });

        lenient().when(gameSessionRepository.findActiveByGameId(any())).thenAnswer(invocation -> {
            GameId id = invocation.getArgument(0);
            return sessionDb.values().stream()
                    .filter(s -> s.getGameId().equals(id)
                            && (s.getStatus() == GameStatus.IN_PROGRESS
                                    || s.getStatus() == GameStatus.WAITING
                                    || s.getStatus() == GameStatus.PAUSED))
                    .findFirst();
        });

        lenient().when(gameDefinitionLocalRepository.findByGameType(any())).thenReturn(Optional.empty());

        // 11-arg production ctor with a (lenient) UserRepository mock: default
        // findByUsername returns Optional.empty() so resolution is a no-op for
        // the non-canonical tests; specific tests override it.
        service = new GameSessionService(
                gameSessionRepository,
                gameRepository,
                null,
                publishGameStatePort,
                null,
                clock,
                new ObjectMapper(),
                gameDefinitionLocalRepository,
                tournamentMatchLocalRepository,
                BUILDING_ID,
                userRepository);
    }

    private Game availableFoosball() {
        return new Game(new GameId("game-1"), GameType.FOOSBALL, "Foosball 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    @Test
    void leaveLobby_removesJoinerFromParticipants() {
        gameDb.put("game-1", availableFoosball());

        GameSession s = service.createLobby(new GameId("game-1"), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s.getId(), new UserId("user-2"));
        service.joinLobby(s.getId(), new UserId("user-3"));

        // Sanity: [user-1, user-2, user-3]
        assertEquals(List.of(new UserId("user-1"), new UserId("user-2"), new UserId("user-3")),
                sessionDb.get(s.getId().value()).getParticipants());

        service.leaveLobby(s.getId(), new UserId("user-2"));

        // After leave: [user-1, user-3] — creator protected, other joiner preserved.
        assertEquals(List.of(new UserId("user-1"), new UserId("user-3")),
                sessionDb.get(s.getId().value()).getParticipants());
        assertEquals(GameStatus.WAITING, sessionDb.get(s.getId().value()).getStatus());
    }

    @Test
    void leaveLobby_supportsUuidCanonicalisation() {
        // joinLobby("bob") → resolveCanonicalUserId("bob") → UUID;
        // leaveLobby("bob") must resolve the SAME way so the removal matches.
        // The creator is not a replicated user (findByUsername returns empty),
        // so its username is preserved raw — mirroring the test scenario where
        // only the joiner has been replicated to the local users table.
        UserId bobUuid = new UserId("22222222-2222-4222-8222-222222222222");
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                new User(bobUuid, "bob", "hash", List.of("PLAYER"), NOW)));
        when(userRepository.findByUsername("creator-1")).thenReturn(Optional.empty());

        gameDb.put("game-1", new Game(new GameId("game-1"), GameType.CHESS, "Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE));

        GameSession s = service.createLobby(new GameId("game-1"), GameType.CHESS, new UserId("creator-1"));
        service.joinLobby(s.getId(), new UserId("bob"));

        // Stored participant is the canonical UUID, not the raw username.
        assertTrue(sessionDb.get(s.getId().value()).getParticipants().contains(bobUuid));

        // leave with the raw username — must canonicalise to the same UUID.
        service.leaveLobby(s.getId(), new UserId("bob"));

        assertFalse(sessionDb.get(s.getId().value()).getParticipants().contains(bobUuid));
        // Creator untouched.
        assertTrue(sessionDb.get(s.getId().value()).getParticipants().contains(new UserId("creator-1")));
        assertEquals(1, sessionDb.get(s.getId().value()).getParticipants().size());
    }

    @Test
    void leaveLobby_creatorCannotLeave() {
        gameDb.put("game-1", availableFoosball());

        GameSession s = service.createLobby(new GameId("game-1"), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s.getId(), new UserId("user-2"));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.leaveLobby(s.getId(), new UserId("user-1")));
        assertTrue(ex.getMessage().contains("creator cannot leave"));
        // Lobby unchanged: both participants still present, status still WAITING.
        assertEquals(2, sessionDb.get(s.getId().value()).getParticipants().size());
        assertEquals(GameStatus.WAITING, sessionDb.get(s.getId().value()).getStatus());
    }

    @Test
    void leaveLobby_nonParticipantLeave_isIdempotent() {
        gameDb.put("game-1", availableFoosball());

        GameSession s = service.createLobby(new GameId("game-1"), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s.getId(), new UserId("user-2"));

        // A user who is NOT in the participants list issues leave — must be a
        // no-op (idempotent), not throw.  This guards against MQTT QoS-1
        // redelivery of a leave event after the participant was already
        // removed, and against a spurious leave from a non-joiner.
        assertDoesNotThrow(() -> service.leaveLobby(s.getId(), new UserId("stranger")));

        assertEquals(List.of(new UserId("user-1"), new UserId("user-2")),
                sessionDb.get(s.getId().value()).getParticipants());
        assertEquals(GameStatus.WAITING, sessionDb.get(s.getId().value()).getStatus());
    }

    @Test
    void leaveLobby_whenStatusNotWaiting_throws() {
        gameDb.put("game-1", availableFoosball());

        GameSession s = service.createLobby(new GameId("game-1"), GameType.FOOSBALL, new UserId("user-1"));
        service.joinLobby(s.getId(), new UserId("user-2"));

        service.startLobby(s.getId());
        assertEquals(GameStatus.IN_PROGRESS, sessionDb.get(s.getId().value()).getStatus());

        // Once the match is started, leave is rejected (the session is no
        // longer in the lobby/WAITING state).  Mirrors the joinLobby guard.
        assertThrows(IllegalStateException.class,
                () -> service.leaveLobby(s.getId(), new UserId("user-2")));
    }

    // ------------------------------------------------------------------
    // Regression for Bug #1: cancelLobby did NOT canonicalise the requester
    // identity, so for multiplayer games the client sends the raw username
    // (LobbyView.serverIdentityForLobby → username for maxPlayers > 1) while
    // createLobby stores the canonical UUID. The UUID-vs-username mismatch
    // made the "Only the lobby creator" guard reject the legitimate creator,
    // leaving the lobby stuck in WAITING / the game machine stuck in LOBBY.
    // These tests prove the canonicalisation fix mirrors joinLobby/leaveLobby.
    // ------------------------------------------------------------------

    @Test
    void cancelLobby_canonicalisesCreatorUsernameToUuid_matchAndCancels() {
        // Replicated user "alice" maps to a stable UUID.
        UserId aliceUuid = new UserId("11111111-1111-4111-8111-111111111111");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(
                new User(aliceUuid, "alice", "hash", List.of("PLAYER"), NOW)));

        gameDb.put("chess-1", new Game(new GameId("chess-1"), GameType.CHESS, "Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE));

        // createLobby canonicalises "alice" → alice-uuid (participants.get(0)).
        GameSession s = service.createLobby(new GameId("chess-1"), GameType.CHESS, new UserId("alice"));
        assertEquals(aliceUuid, sessionDb.get(s.getId().value()).getParticipants().get(0));
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("chess-1").getStatus());

        // The multiplayer client sends the raw USERNAME in lobby/cancel —
        // cancelLobby must canonicalise it back to the UUID to match.
        assertDoesNotThrow(() -> service.cancelLobby(s.getId(), new UserId("alice")));

        // The lobby is now torn down and the game machine released.
        assertEquals(GameStatus.ABORTED, sessionDb.get(s.getId().value()).getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, gameDb.get("chess-1").getStatus());
    }

    @Test
    void cancelLobby_withRawUuidWhenCreatorStoredAsUuid_alsoMatches() {
        // Single-player / upgraded-client flow: the identity is already a
        // UUID. findByUsername misses it (UUID is not a username), so
        // resolution is an idempotent no-op.
        UserId aliceUuid = new UserId("alice-uuid");
        when(userRepository.findByUsername("alice-uuid")).thenReturn(Optional.empty());

        gameDb.put("roulette-1", new Game(new GameId("roulette-1"), GameType.ROULETTE, "Roulette",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE));

        GameSession s = service.createLobby(new GameId("roulette-1"), GameType.ROULETTE, aliceUuid);
        assertEquals(aliceUuid, sessionDb.get(s.getId().value()).getParticipants().get(0));

        // cancel with the same raw UUID — must match directly.
        assertDoesNotThrow(() -> service.cancelLobby(s.getId(), aliceUuid));

        assertEquals(GameStatus.ABORTED, sessionDb.get(s.getId().value()).getStatus());
        assertEquals(GameMachineStatus.AVAILABLE, gameDb.get("roulette-1").getStatus());
    }

    @Test
    void cancelLobby_rejectsNonCreator_evenWithCanonicalisation() {
        // "bob" is a replicated user (canonicalisable to a UUID), but is NOT
        // the creator — the canonicalised identity still must not match.
        UserId aliceUuid = new UserId("11111111-1111-4111-8111-111111111111");
        UserId bobUuid = new UserId("22222222-2222-4222-8222-222222222222");
        when(userRepository.findByUsername(aliceUuid.value())).thenReturn(Optional.empty());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                new User(bobUuid, "bob", "hash", List.of("PLAYER"), NOW)));

        gameDb.put("chess-1", new Game(new GameId("chess-1"), GameType.CHESS, "Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE));

        // Creator stored as aliceUuid (resolveCanonicalUserId misses — the
        // UUID is not a replicated username — and keeps it verbatim).
        GameSession s = service.createLobby(new GameId("chess-1"), GameType.CHESS, aliceUuid);
        assertEquals(aliceUuid, sessionDb.get(s.getId().value()).getParticipants().get(0));

        // Non-creator "bob" → canonicalises to bob-uuid → mismatch with
        // alice-uuid → IllegalStateException, lobby unchanged.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.cancelLobby(s.getId(), new UserId("bob")));
        assertTrue(ex.getMessage().contains("Only the lobby creator can cancel"));
        assertEquals(GameStatus.WAITING, sessionDb.get(s.getId().value()).getStatus());
        assertEquals(GameMachineStatus.LOBBY, gameDb.get("chess-1").getStatus());
    }
}
