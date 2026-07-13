package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.SlotResult;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Permanent backstop for the {@code MyStats doesn't show the slot row} bug
 * (root cause: the Game Client Emulator emitted the participant's
 * <em>username</em> as the server-facing identity for single-player games,
 * so the Central {@code player_statistics} read-model was keyed on the
 * username — invisible to {@code GET /api/players/me/statistics}, which
 * resolves the authenticated user's stable id / UUID from the JWT). Two
 * independent lines of defence are exercised here at the service level:
 *
 * <ol>
 *   <li><b>Server-side canonicalisation</b> — the production
 *       {@link GameSessionService} 11-arg constructor injects a
 *       {@link UserRepository} and resolves each participant (and the
 *       winner) through {@code findByUsername} before persisting the
 *       {@link GameSession} and emitting the
 *       {@code GAME_SESSION_COMPLETED} outbox payload. This guarantees a
 *       UUID-keyed projection for every game type / role / lobby flow
 *       (slot single-player, multiplayer walk-in, lobby create/join),
 *       making the fix immune to a client forgetting to send the UUID.</li>
 *   <li><b>Legacy-contract preservation</b> — when no
 *       {@link UserRepository} is wired (the 10-arg backward-compat ctor
 *       used by existing unit-test slices), resolution is skipped and the
 *       raw identity is preserved byte-identical, so the historical
 *       behaviour of {@code SinglePlayerGamePlayStatisticsTest} /
 *       {@code MultiPlayerGamePlayStatisticsTest} is unchanged.</li>
 * </ol>
 *
 * <p>Pure-Mockito slice, real {@link GameSessionService}, no Spring context
 * (mirrors {@link SinglePlayerGamePlayStatisticsTest}).</p>
 */
@ExtendWith(MockitoExtension.class)
class GameSessionServiceParticipantResolutionTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private static final String BUILDING_ID = "building-1";
    private static final UserId PLAYER_USERNAME = new UserId("alice");
    private static final UserId PLAYER_UUID = new UserId("11111111-1111-4111-8111-111111111111");
    private static final GameId GAME_ID = new GameId("slot-1");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock UserRepository userRepository;
    @Mock Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private GameSessionService sessionService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        sessionService = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper,
                gameDefinitionLocalRepository, tournamentMatchLocalRepository,
                BUILDING_ID, userRepository);
    }

    private Game availableSlot() {
        return new Game(GAME_ID, GameType.SLOT_MACHINE, "Slot Machine 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    private GameDefinitionLocal slotDef() {
        return new GameDefinitionLocal(GameType.SLOT_MACHINE, "Slot Machine", 1, 1, false, null, NOW);
    }

    private User replicated() {
        return new User(PLAYER_UUID, "alice", "hash", List.of("PLAYER"), NOW);
    }

    @Test
    void startWithUsername_resolvesToUuid_inStoredSessionAndOutboxPayload() throws Exception {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(replicated()));
        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.of(slotDef()));
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(availableSlot()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = sessionService.start(GAME_ID, GameType.SLOT_MACHINE,
                List.of(PLAYER_USERNAME), null);

        assertThat(s.getParticipants()).as("stored participants are canonical UUIDs")
                .containsExactly(PLAYER_UUID);
        assertThat(s.getParticipants()).as("username must NOT leak into the session")
                .noneMatch(p -> "alice".equals(p.value()));

        when(gameSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(availableSlot()));

        // SlotResult carries the username as visitorId (as a legacy GUI would);
        // the resolution at end() re-canonicalises the winnerId for the payload.
        SlotResult winResult = new SlotResult(PLAYER_USERNAME.value(), 10, 100, 250, 150,
                WinCondition.WIN);
        sessionService.end(s.getId(), winResult);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("GAME_SESSION_COMPLETED");
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("participants").get(0).asText())
                .as("outbox participants list is UUID-keyed")
                .isEqualTo(PLAYER_UUID.value());
        assertThat(payload.get("winnerId").asText())
                .as("winnerId is resolved to the canonical UUID")
                .isEqualTo(PLAYER_UUID.value());
        assertThat(payload.get("winCondition").asText()).isEqualTo("WIN");
    }

    @Test
    void startWithUuid_isIdempotent_resolutionLeavesItUntouched() {
        // findByUsername misses a UUID → resolution keeps it verbatim.
        when(userRepository.findByUsername(PLAYER_UUID.value())).thenReturn(Optional.empty());
        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.of(slotDef()));
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(availableSlot()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = sessionService.start(GAME_ID, GameType.SLOT_MACHINE,
                List.of(PLAYER_UUID), null);

        assertThat(s.getParticipants()).containsExactly(PLAYER_UUID);
    }

    @Test
    void legacyCtor_withoutUserRepository_preservesRawParticipants() {
        // Pre-resolution behaviour: 10-arg legacy ctor passes a null
        // UserRepository, so resolution is skipped — the raw participant is
        // preserved byte-identical (GameSessionServiceTournamentTest /
        // SinglePlayerGamePlayStatisticsTest rely on this).
        GameSessionService legacy = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper,
                gameDefinitionLocalRepository, tournamentMatchLocalRepository, BUILDING_ID);

        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.of(slotDef()));
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(availableSlot()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = legacy.start(GAME_ID, GameType.SLOT_MACHINE,
                List.of(PLAYER_USERNAME), null);

        assertThat(s.getParticipants()).as("legacy ctor preserves raw username")
                .containsExactly(PLAYER_USERNAME);
        assertThat(s.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
    }

    @Test
    void createLobby_resolvesCreatorUsername_toCanonicalUuid() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(replicated()));
        Game chessGame = new Game(new GameId("chess-1"), GameType.CHESS, "Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
        when(gameSessionRepository.findActiveByGameId(chessGame.getId())).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(chessGame.getId())).thenReturn(Optional.of(chessGame));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession lobby = sessionService.createLobby(chessGame.getId(), GameType.CHESS, PLAYER_USERNAME);

        assertThat(lobby.getParticipants()).as("lobby creator is resolved to UUID")
                .containsExactly(PLAYER_UUID);
        assertThat(lobby.getStatus()).isEqualTo(GameStatus.WAITING);
    }

    @Test
    void joinLobby_resolvesJoinerUsername_toCanonicalUuid_addsResolvedToSession() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(
                new User(new UserId("22222222-2222-4222-8222-222222222222"),
                        "bob", "hash", List.of("PLAYER"), NOW)));
        UserId joiner = new UserId("bob");
        UserId creator = new UserId("11111111-1111-4111-8111-111111111111");
        GameId chessGameId = new GameId("chess-join-1");
        Game chessGame = new Game(chessGameId, GameType.CHESS, "Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.LOBBY);
        GameSessionId sessionId = new GameSessionId("sess-join-1");
        GameSession existing = new GameSession(
                sessionId, chessGameId, GameType.CHESS, new BuildingId(BUILDING_ID),
                GameStatus.WAITING, NOW, null, null, null, null, null, List.of(creator));

        when(gameSessionRepository.findById(sessionId)).thenReturn(Optional.of(existing));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession joined = sessionService.joinLobby(sessionId, joiner);

        assertThat(joined.getParticipants())
                .as("joiner resolved to UUID, creator kept as-is")
                .containsExactly(creator, new UserId("22222222-2222-4222-8222-222222222222"));
        assertThat(joined.getParticipants())
                .as("raw username must not leak into the participants list")
                .noneMatch(p -> "bob".equals(p.value()));
    }
}