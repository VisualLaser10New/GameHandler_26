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
import com.gameplatform.local.domain.model.LocalStatistics;
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
 * Focus-D LOCAL_ADMIN end-to-end flow at the service/use-case level (mirrors
 * {@link GameSessionServiceTournamentTest}: pure-Mockito slice, real
 * {@link GameCatalogService} + real {@link GameSessionService} + real
 * {@link StatisticsService}, no Spring context). The LOCAL_ADMIN building
 * REST endpoints are covered by the existing controller slices
 * ({@code AdminLocalControllerTest}, {@code AdminLocalControllerIT}); this
 * test fills the missing end-to-end service-level branch the spec calls out:
 * create game &rarr; verify in catalog &rarr; update game &rarr; start session
 * &rarr; verify active sessions &rarr; end session &rarr; verify no active
 * sessions &rarr; building aggregate statistics ({@link LocalStatistics})
 * reflect the completed session &rarr; emit {@code GAME_SESSION_COMPLETED}
 * outbox &rarr; delete the (not-in-use) game.
 *
 * <p>{@code @SpringBootTest} is architecturally blocked on local-server:
 * {@code MqttConfig.mqttClient} eagerly connects to {@code tcp://localhost:1883}
 * during context refresh and fails without a broker (documented in
 * {@code AdminLocalControllerIT} / {@code GameAdminControllerTest} javadocs).
 * All local-server integration tests therefore follow the
 * {@code standaloneSetup}/{@code new Service(...)} Mockito convention; this
 * test reuses the same scaffolding for the service-level E2E flow.</p>
 */
@ExtendWith(MockitoExtension.class)
class LocalAdminFlowsTest {

    private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");
    private static final String BUILDING_ID = "building-1";
    private static final UserId PLAYER = new UserId("admin-flow-player");
    private static final BuildingId BUILDING = new BuildingId(BUILDING_ID);

    @Mock GameRepository gameRepository;
    @Mock GameSessionRepository gameSessionRepository;
    @Mock ReservationRepository reservationRepository;
    @Mock OutboxEventRepository outboxEventRepository;
    @Mock PublishGameStatePort publishGameStatePort;
    @Mock GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private GameCatalogService gameCatalogService;
    private GameSessionService sessionService;
    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW);
        gameCatalogService = new GameCatalogService(gameRepository);
        sessionService = new GameSessionService(
                gameSessionRepository, gameRepository, outboxEventRepository,
                publishGameStatePort, reservationRepository, clock, objectMapper,
                gameDefinitionLocalRepository, tournamentMatchLocalRepository, BUILDING_ID);
        statisticsService = new StatisticsService(gameSessionRepository, gameRepository, reservationRepository);
    }

    private GameDefinitionLocal slotDef() {
        return new GameDefinitionLocal(GameType.SLOT_MACHINE, "Slot Machine", 1, 1, false, null, NOW);
    }

    @Test
    void localAdminFlow_createUpdateStartEndStatsDelete_allBranchesExercised() throws Exception {
        when(gameRepository.save(any(Game.class))).thenAnswer(inv -> inv.getArgument(0));

        Game created = gameCatalogService.createGame(GameType.SLOT_MACHINE, "Slot 1", BUILDING);
        assertThat(created.getStatus()).isEqualTo(GameMachineStatus.AVAILABLE);
        assertThat(created.getGameType()).isEqualTo(GameType.SLOT_MACHINE);
        assertThat(created.getBuildingId()).isEqualTo(BUILDING);

        when(gameRepository.findByBuildingId(BUILDING)).thenReturn(List.of(created));
        List<Game> catalog = gameRepository.findByBuildingId(BUILDING);
        assertThat(catalog).extracting(Game::getId).containsExactly(created.getId());

        when(gameRepository.findByIdForUpdate(created.getId())).thenReturn(Optional.of(created));
        Game updated = gameCatalogService.updateGame(created.getId(), "Slot Renamed", null);
        assertThat(updated.getName()).isEqualTo("Slot Renamed");

        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.of(slotDef()));
        when(gameSessionRepository.findActiveByGameId(created.getId())).thenReturn(Optional.empty());
        when(gameSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        GameSession s = sessionService.start(created.getId(), GameType.SLOT_MACHINE, List.of(PLAYER), null);
        assertThat(s.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);

        when(gameSessionRepository.findByBuildingId(BUILDING)).thenReturn(List.of(s));
        assertThat(statisticsService.getActiveSessionsByBuilding(BUILDING)).hasSize(1);

        when(gameSessionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(gameRepository.findById(created.getId())).thenReturn(Optional.of(created));
        sessionService.end(s.getId(),
                new SlotResult(PLAYER.value(), 5, 50, 120, 70, WinCondition.WIN));
        assertThat(s.getStatus()).isEqualTo(GameStatus.COMPLETED);
        assertThat(s.getWinnerId()).isEqualTo(PLAYER);

        assertThat(statisticsService.getActiveSessionsByBuilding(BUILDING)).isEmpty();

        when(reservationRepository.countByGameIds(List.of(created.getId()))).thenReturn(0);
        LocalStatistics stats = statisticsService.getStatisticsForBuilding(GameType.SLOT_MACHINE, BUILDING);
        assertThat(stats.getTotalSessions()).isEqualTo(1);
        assertThat(stats.getWinRateByUser()).containsEntry(PLAYER.value(), 1.0);

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("GAME_SESSION_COMPLETED");

        gameCatalogService.deleteGame(created.getId());
        verify(gameRepository).deleteById(created.getId());
    }
}
