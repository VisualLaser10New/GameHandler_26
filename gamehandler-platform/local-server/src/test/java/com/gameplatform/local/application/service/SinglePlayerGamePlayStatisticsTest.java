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
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.domain.result.SlotResult;
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
 * Focus-B single-player slot-machine E2E flow at the service/use-case level
 * (mirrors {@link GameSessionServiceTournamentTest}: pure-Mockito slice, real
 * {@link GameSessionService} + real {@link StatisticsService}, no Spring
 * context). Exercises start &rarr; end with a winning {@link SlotResult} and
 * asserts:
 *
 * <ul>
 *   <li>the session transitions to {@code COMPLETED} with {@code winnerId}
 *       populated (single-player slot: minPlayers=maxPlayers=1);</li>
 *   <li>an {@code outbox_events} row with event_type {@code GAME_SESSION_COMPLETED}
 *       is written, carrying the enriched payload ({@code gameType=SLOT_MACHINE},
 *       {@code participants=[player]}, {@code winnerId}, {@code winCondition=WIN});</li>
 *   <li>the on-demand {@link StatisticsService#getPlayerStatistics} read-model
 *       returns a row for the player with {@code matchesPlayed=1, matchesWon=1}
 *       for the {@code SLOT_MACHINE} game type (PIANO &sect;2.5 — local offline
 *       replica computed from {@code game_sessions}+{@code session_participants}).</li>
 * </ul>
 *
 * <p>No slot "spin" service is exercised: per PIANO the physical game device
 * owns the per-round spin loop over MQTT; the Local {@code GameSessionService}
 * only opens (start) and closes (end) the session. The integration with the
 * Central {@code player_match_facts} projection is exercised end-to-end by
 * Focus-A {@code TournamentFlowWithPlayerStatisticsIT} (it feeds synthetic
 * {@code GAME_SESSION_COMPLETED} events through the central
 * {@code SyncEventProcessor}); this test asserts the Local outbox + read-model
 * only, as permitted by the spec.</p>
 */
@ExtendWith(MockitoExtension.class)
class SinglePlayerGamePlayStatisticsTest {

    private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
    private static final String BUILDING_ID = "building-1";
    private static final UserId PLAYER = new UserId("slot-player");
    private static final GameId GAME_ID = new GameId("slot-1");

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

    private Game availableSlot() {
        return new Game(GAME_ID, GameType.SLOT_MACHINE, "Slot Machine 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    private GameDefinitionLocal slotDef() {
        return new GameDefinitionLocal(GameType.SLOT_MACHINE, "Slot Machine", 1, 1, false, null, NOW);
    }

    @Test
    void slotSession_startToEndWithWin_emitsCompletedOutboxAndAggregatesPlayerStatistics() throws Exception {
        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.of(slotDef()));
        when(gameSessionRepository.findActiveByGameId(GAME_ID)).thenReturn(Optional.empty());
        when(gameRepository.findByIdForUpdate(GAME_ID)).thenReturn(Optional.of(availableSlot()));
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = sessionService.start(GAME_ID, GameType.SLOT_MACHINE, List.of(PLAYER), null);

        assertThat(s.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(s.getParticipants()).containsExactly(PLAYER);

        when(gameSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(gameRepository.findById(GAME_ID)).thenReturn(Optional.of(availableSlot()));

        SlotResult winResult = new SlotResult(PLAYER.value(), 10, 100, 250, 150, WinCondition.WIN);
        sessionService.end(s.getId(), winResult);

        assertThat(s.getStatus()).isEqualTo(GameStatus.COMPLETED);
        assertThat(s.getWinnerId()).isEqualTo(PLAYER);
        assertThat(s.getWinCondition()).isEqualTo(WinCondition.WIN);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEvent event = outboxCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("GAME_SESSION_COMPLETED");
        var payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.get("gameType").asText()).isEqualTo("SLOT_MACHINE");
        assertThat(payload.get("winnerId").asText()).isEqualTo(PLAYER.value());
        assertThat(payload.get("winCondition").asText()).isEqualTo("WIN");
        assertThat(payload.get("participants").toString()).contains(PLAYER.value());
        assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");

        when(gameSessionRepository.findByParticipant(PLAYER)).thenReturn(List.of(s));
        List<PlayerStatisticsDto> stats = statisticsService.getPlayerStatistics(PLAYER);
        assertThat(stats).hasSize(1);
        PlayerStatisticsDto dto = stats.get(0);
        assertThat(dto.userId()).isEqualTo(PLAYER.value());
        assertThat(dto.gameType()).isEqualTo(GameType.SLOT_MACHINE);
        assertThat(dto.matchesPlayed()).isEqualTo(1);
        assertThat(dto.matchesWon()).isEqualTo(1);
        assertThat(dto.lastPlayedAt()).isEqualTo(NOW);
    }
}
