package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.LocalAdminBuildingSyncService;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetBuildingStatisticsUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingActiveSessionsUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingGamesUseCase;
import com.gameplatform.local.domain.ports.in.ManageGameCatalogUseCase;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.local.infrastructure.security.LocalAdminBuildingAuthorizationManager;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameMachineStatus;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.CreateGameRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;

/**
 * Slice test for {@link AdminLocalController} and the
 * {@code BuildingNotRegisteredToAdminException → 403} mapping in
 * {@link GlobalExceptionHandler}.
 *
 * <p><b>Why a Mockito standaloneSetup slice and not a full
 * {@code @SpringBootTest} (H2) IT:</b> the local-server's
 * {@code @SpringBootApplication} eagerly instantiates the MQTT client
 * ({@code MqttConfig.mqttClient}, connect to {@code tcp://localhost:1883})
 * during context refresh, which fails in CI/dev (no broker). This is the same
 * documented constraint already noted in {@code GameOptimisticLockGuardTest}
 * and {@code UserRepositoryAdapterOrderingGuardIT} javadocs, which is why every
 * existing local-server controller test ({@code GameControllerTest},
 * {@code StatisticsControllerTest}, {@code InternalSyncControllerTest}, …) uses
 * the same {@code MockMvcBuilders.standaloneSetup} + Mockito convention. This
 * test mirrors that exact scaffolding (the spec's sanctioned fallback when full
 * IT is too heavy): the 4 use cases and
 * {@link LocalAdminBuildingAuthorizationManager} are mocked, the
 * {@link GlobalExceptionHandler} is wired via {@code setControllerAdvice}, and a
 * real {@link ObjectMapper} plus the literal building id {@code "building-1"}
 * are constructor-injected (matching {@code StatisticsControllerTest}).</p>
 *
 * <p>Spring Security's class-level {@code @PreAuthorize("hasRole('LOCAL_ADMIN')")}
 * is not enforced in standalone MockMvc (consistent with the sibling tests); the
 * per-request building enforcement is verified by stubbing
 * {@code canManageBuilding(...)} to {@code true}/{@code false}.</p>
 */
@ExtendWith(MockitoExtension.class)
class AdminLocalControllerIT {

    private static final String BUILDING_ID = "building-1";

    @Mock private ManageGameCatalogUseCase manageGameCatalogUseCase;
    @Mock private ListBuildingGamesUseCase listBuildingGamesUseCase;
    @Mock private ListBuildingActiveSessionsUseCase listBuildingActiveSessionsUseCase;
    @Mock private GetBuildingStatisticsUseCase getBuildingStatisticsUseCase;
    @Mock private LocalAdminBuildingAuthorizationManager authorizationManager;
    @Mock private GameDefinitionLocalRepository gameDefinitionLocalRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new AdminLocalController(
                manageGameCatalogUseCase,
                listBuildingGamesUseCase,
                listBuildingActiveSessionsUseCase,
                getBuildingStatisticsUseCase,
                objectMapper,
                authorizationManager,
                BUILDING_ID,
                gameDefinitionLocalRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        // Mirror JwtAuthenticationFilter: principal = username String, authenticated.
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "admin-1", null,
                List.of(new SimpleGrantedAuthority("ROLE_LOCAL_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Game sampleGame() {
        return new Game(new GameId("game-chess-1"), GameType.CHESS, "Chess Table 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    @Test
    void getDevicesReturns200WithSeededGamesWhenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(listBuildingGamesUseCase.getByBuilding(eq(new BuildingId(BUILDING_ID))))
                .thenReturn(List.of(sampleGame()));

        mvc.perform(get("/api/admin/local/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("game-chess-1"))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[0].name").value("Chess Table 1"))
                .andExpect(jsonPath("$[0].buildingId").value(BUILDING_ID))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void getDevicesReturns403WhenAdminNotBoundToBuilding() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(get("/api/admin/local/devices"))
                .andExpect(status().isForbidden());
    }

    @Test
    void postGamesReturns201AndGameAppearsInDevices() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameDefinitionLocalRepository.existsByGameType(any())).thenReturn(true);
        Game created = new Game(new GameId("new-1"), GameType.CHESS, "New Chess",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
        when(manageGameCatalogUseCase.createGame(eq(GameType.CHESS), eq("New Chess"),
                eq(new BuildingId(BUILDING_ID))))
                .thenReturn(created);
        when(listBuildingGamesUseCase.getByBuilding(eq(new BuildingId(BUILDING_ID))))
                .thenReturn(List.of(created));

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGameRequestDto("CHESS", "New Chess"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value("new-1"))
                .andExpect(jsonPath("$.name").value("New Chess"));

        // The newly created game now appears in the building device listing.
        mvc.perform(get("/api/admin/local/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("new-1"));
    }

    @Test
    void postGamesWithInvalidGameTypeReturns400() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateGameRequestDto("NOT_A_GAME", "Whatever"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteGameReturns204WhenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        doNothing().when(manageGameCatalogUseCase).deleteGame(eq(new GameId("game-chess-1")));

        mvc.perform(delete("/api/admin/local/games/game-chess-1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getStatisticsRequiresGameTypeQueryParam() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);

        mvc.perform(get("/api/admin/local/statistics"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatisticsReturnsBuildingScopedStats() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        LocalStatistics stats = new LocalStatistics(GameType.CHESS, 3, 120.0, 2, new HashMap<>());
        when(getBuildingStatisticsUseCase.getStatisticsForBuilding(eq(GameType.CHESS),
                eq(new BuildingId(BUILDING_ID))))
                .thenReturn(stats);

        mvc.perform(get("/api/admin/local/statistics").param("gameType", "chess"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("CHESS"))
                .andExpect(jsonPath("$.totalSessions").value(3))
                .andExpect(jsonPath("$.totalReservations").value(2));
    }

    @Test
    void getActiveSessionsReturns200WithSessionDtos() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        GameSession session = new GameSession(
                new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId(BUILDING_ID), GameStatus.IN_PROGRESS,
                Instant.parse("2026-02-01T10:00:00Z"),
                null, null, null, null, null, List.of());
        when(listBuildingActiveSessionsUseCase.getActiveSessionsByBuilding(
                eq(new BuildingId(BUILDING_ID))))
                .thenReturn(List.of(session));

        mvc.perform(get("/api/admin/local/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"))
                .andExpect(jsonPath("$[0].gameId").value("g1"))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));
    }

    @Test
    void internalMetadataSyncDelegatesToService() throws Exception {
        // InternalMetadataController is gated by InternalApiKeyFilter (servlet filter),
        // NOT by JWT/role — verified here at the controller layer only.
        com.gameplatform.local.application.service.LocalAdminBuildingSyncService syncService =
                org.mockito.Mockito.mock(LocalAdminBuildingSyncService.class);
        org.mockito.Mockito.doNothing().when(syncService).applyEvents(any());
        MockMvc internalMvc = MockMvcBuilders.standaloneSetup(new InternalMetadataController(syncService)).build();

        internalMvc.perform(put("/internal/metadata/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(syncService).applyEvents(any());
    }
}