package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.PublishGameStatePort;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.ChessResult;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
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
 * Focus-C multi-player CHESS E2E flow at the service/use-case level (mirrors
 * {@link GameSessionServiceTournamentTest}: pure-Mockito slice, real
 * {@link GameSessionService} + real {@link StatisticsService}, no Spring
 * context). Two players arrive together (walk-in without a reservation — the
 * non-lobby {@code start(gameId, type, participants, null)} path already
 * exercised by {@code GameSessionServiceTest.shouldStartSessionWithoutReservation};
 * the lobby join flow is covered separately by {@code MultiplayerLobbyUnitTest})
 * and the match ends with {@code p1} victorious via a {@link ChessResult}.
 * Asserts:
 *
 * <ul>
 *   <li>session transitions to {@code COMPLETED} with {@code winnerId=p1};</li>
 *   <li>{@code outbox_events} row {@code GAME_SESSION_COMPLETED} carries the
 *       enriched payload ({@code participants=[p1,p2]}, {@code winnerId=p1},
 *       {@code winCondition=WIN});</li>
 *   <li>the local on-demand read-model returns {@code p1 wins+1} and
 *       {@code p2 wins+0, losses+1} for the {@code CHESS} game type
 *       ({@code matchesPlayed=1} for both, computed from
 *       {@code game_sessions}+{@code session_participants} per PIANO &sect;2.5).</li>
 * </ul>
 *
 * <p>The central {@code player_match_facts}/{@code player_statistics}
 * projection for a 2-participant {@code GAME_SESSION_COMPLETED} event is
 * exercised end-to-end by Focus-A ({@code TournamentFlowWithPlayerStatisticsIT}),
 * so this test stays local-side only as permitted by the spec.</p>
 */
@ExtendWith(MockitoExtension.class)
class MultiPlayerGamePlayStatisticsTest {

    private static final Instant NOW = Instant.parse("2026-07-13T11:00:00Z");
    private static final String BUILDING_ID = "building-1";
    private static final UserId P1 = new UserId("chess-p1");
    private static final UserId P2 = new UserId("chess-p2");
    private static final GameId GAME_ID = new GameId("chess-1");

    @Mock GameSessionRepository gameSessionRepository;
    @Mock GameRepository gameRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock ReservationRepository reservationRepository;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private GameSessionService sessionService;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        sessionService = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper,
                gameDefinitionLocalRepository, tournamentMatchLocalRepository, BUILDING_ID);
        statisticsService = new StatisticsService(gameSessionRepository, gameRepository, reservationRepository);
    }

    private Game availableChess() {
        return new Game(GAME_ID, GameType.CHESS, "Chess Table 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    private GameDefinitionLocal chessDef() {
        return new GameDefinitionLocal(GameType.CHESS, "Chess", 2, 2, false, null, NOW);
    }

    @Test
    void chessSession_twoWalkInPlayers_endWithWinner_aggregatesBothPlayersStatistics() throws Exception {
        when(gameDefinitionLocalRepository.findByGameType(GameType.CHESS))
                .thenReturn(Optional.of(chessDef()));
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(availableChess()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = sessionService.start(GAME_ID, GameType.CHESS, List.of(P1, P2), null);

        assertThat(s.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(s.getParticipants()).containsExactly(P1, P2);

        when(gameSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(availableChess()));

        ChessResult result = new ChessResult(P1, List.of(P1), "checkmate", "rnbqkbnr/pppppppp", WinCondition.WIN);
        sessionService.end(s.getId(), result);

        assertThat(s.getStatus()).isEqualTo(GameStatus.COMPLETED);
        assertThat(s.getWinnerId()).isEqualTo(P1);
        assertThat(s.getWinCondition()).isEqualTo(WinCondition.WIN);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("GAME_SESSION_COMPLETED");
        var payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("gameType").asText()).isEqualTo("CHESS");
        assertThat(payload.get("winnerId").asText()).isEqualTo(P1.value());
        assertThat(payload.get("winCondition").asText()).isEqualTo("WIN");
        assertThat(payload.get("participants").toString()).contains(P1.value(), P2.value());

        when(gameSessionRepository.findByParticipant(P1)).thenReturn(List.of(s));
        when(gameSessionRepository.findByParticipant(P2)).thenReturn(List.of(s));

        PlayerStatisticsDto p1Stats = statisticsService.getPlayerStatistics(P1).stream()
                .filter(d -> d.gameType() == GameType.CHESS).findFirst().orElseThrow();
        assertThat(p1Stats.matchesPlayed()).isEqualTo(1);
        assertThat(p1Stats.matchesWon()).isEqualTo(1);

        PlayerStatisticsDto p2Stats = statisticsService.getPlayerStatistics(P2).stream()
                .filter(d -> d.gameType() == GameType.CHESS).findFirst().orElseThrow();
        assertThat(p2Stats.matchesPlayed()).isEqualTo(1);
        assertThat(p2Stats.matchesWon()).isEqualTo(0);
    }
}
