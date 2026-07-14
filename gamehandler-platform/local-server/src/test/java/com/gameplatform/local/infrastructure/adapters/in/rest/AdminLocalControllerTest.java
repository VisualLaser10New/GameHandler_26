package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.Game;
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
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.UpdateGameRequestDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Slice test for {@link AdminLocalController} covering the LOCAL_ADMIN
 * building-scoped endpoints (PIANO §7.B — VIOL-2 follow-ups).
 *
 * <p>Follows the local {@code standaloneSetup} + Mockito convention already
 * used by {@code DeviceRegistrationControllerTest}: Spring Security method
 * security ({@code @PreAuthorize("hasRole('LOCAL_ADMIN') or hasRole('PLATFORM_ADMIN')")}
 * at class level) is intentionally NOT enforced here (standaloneSetup
 * bypasses it); the {@link LocalAdminBuildingAuthorizationManager} mock
 * fully controls building authorization, simulating both the authorized
 * LOCAL_ADMIN/PLATFORM_ADMIN and the unauthorized (different-building)
 * LOCAL_ADMIN cases. The negative role-enforcement contract is covered by
 * the sibling {@code AdminLocalControllerRoleEnforcementTest} via
 * {@code @WebMvcTest} + {@code @WithMockUser}.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET /api/admin/local/devices} 200/403</li>
 *   <li>{@code GET /api/admin/local/sessions/active} 200/403</li>
 *   <li>{@code GET /api/admin/local/statistics} 200/400 (missing gameType)/403</li>
 *   <li>{@code POST /api/admin/local/games} 201/403/400 (game definition missing locally)/400 (unknown gameType)</li>
 *   <li>{@code PUT /api/admin/local/games/{gameId}} 200/403</li>
 *   <li>{@code DELETE /api/admin/local/games/{gameId}} 204/403</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminLocalControllerTest {

    private static final String BUILDING_ID = "building-1";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private ManageGameCatalogUseCase manageGameCatalogUseCase;
    @Mock private ListBuildingGamesUseCase listBuildingGamesUseCase;
    @Mock private ListBuildingActiveSessionsUseCase listBuildingActiveSessionsUseCase;
    @Mock private GetBuildingStatisticsUseCase getBuildingStatisticsUseCase;
    @Mock private LocalAdminBuildingAuthorizationManager authorizationManager;
    @Mock private GameDefinitionLocalRepository gameDefinitionLocalRepository;
    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AdminLocalController(manageGameCatalogUseCase, listBuildingGamesUseCase,
                                listBuildingActiveSessionsUseCase, getBuildingStatisticsUseCase,
                                objectMapper, authorizationManager, BUILDING_ID, gameDefinitionLocalRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Game sampleGame() {
        return new Game(new GameId("g1"), GameType.CHESS, "Chess Table",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
    }

    @Test
    void getDevices_returns200_whenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(listBuildingGamesUseCase.getByBuilding(new BuildingId(BUILDING_ID)))
                .thenReturn(List.of(sampleGame()));
        when(gameDefinitionLocalRepository.findByGameType(GameType.CHESS))
                .thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/local/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("g1"))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[0].buildingId").value(BUILDING_ID));
    }

    @Test
    void getDevices_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(get("/api/admin/local/devices"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listBuildingGamesUseCase);
    }

    @Test
    void getActiveSessions_returns200_whenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(listBuildingActiveSessionsUseCase.getActiveSessionsByBuilding(new BuildingId(BUILDING_ID)))
                .thenReturn(List.of());

        mvc.perform(get("/api/admin/local/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getActiveSessions_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(get("/api/admin/local/sessions/active"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(listBuildingActiveSessionsUseCase);
    }

    @Test
    void getStatistics_returns200_whenAuthorizedAndGameTypeProvided() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        LocalStatistics stats = new LocalStatistics(GameType.CHESS, 5, 12.5, 2, Map.of("u1", 0.5));
        when(getBuildingStatisticsUseCase.getStatisticsForBuilding(GameType.CHESS, new BuildingId(BUILDING_ID)))
                .thenReturn(stats);

        mvc.perform(get("/api/admin/local/statistics").param("gameType", "CHESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("CHESS"))
                .andExpect(jsonPath("$.totalSessions").value(5));
    }

    @Test
    void getStatistics_400_whenGameTypeMissing() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);

        mvc.perform(get("/api/admin/local/statistics"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(getBuildingStatisticsUseCase);
    }

    @Test
    void getStatistics_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(get("/api/admin/local/statistics").param("gameType", "CHESS"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(getBuildingStatisticsUseCase);
    }

    @Test
    void createGame_returns201_whenAuthorizedAndDefinitionAvailable() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameDefinitionLocalRepository.existsByGameType(GameType.CHESS)).thenReturn(true);
        when(manageGameCatalogUseCase.createGame(GameType.CHESS, "Chess Table 2", new BuildingId(BUILDING_ID)))
                .thenReturn(sampleGame());
        when(gameDefinitionLocalRepository.findByGameType(GameType.CHESS))
                .thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess Table 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value("g1"))
                .andExpect(jsonPath("$.gameType").value("CHESS"));

        verify(manageGameCatalogUseCase).createGame(GameType.CHESS, "Chess Table 2", new BuildingId(BUILDING_ID));
    }

    @Test
    void createGame_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess Table 2\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageGameCatalogUseCase);
        verifyNoInteractions(gameDefinitionLocalRepository);
    }

    @Test
    void createGame_400_whenGameDefinitionNotAvailableLocally() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameDefinitionLocalRepository.existsByGameType(GameType.CHESS)).thenReturn(false);

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess Table 2\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(manageGameCatalogUseCase);
    }

    @Test
    void createGame_returns201_forSlotMachine_afterSeedFix() throws Exception {
        // Regression: SLOT_MACHINE non era presente in game_definitions_local
        // (DATABASE_INIT_ADDBUG — il seed centrale NON emette outbox
        // GAME_DEFINITION_UPSERTED, quindi la replica non raggiungeva il local).
        // existsByGameType(SLOT_MACHINE) ritornava false -> POST /games 400 ->
        // la GUI "Add game" non creava nessun nuovo gioco. Il seed locale negli
        // init.sql locali (mysql-local/init*.sql) ora popola la tabella: questo
        // test PROVA che, con existsByGameType(SLOT_MACHINE)=true, la POST 201.
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameDefinitionLocalRepository.existsByGameType(GameType.SLOT_MACHINE)).thenReturn(true);
        Game slot = new Game(new GameId("g-slot"), GameType.SLOT_MACHINE, "Slot 1",
                new BuildingId(BUILDING_ID), GameMachineStatus.AVAILABLE);
        when(manageGameCatalogUseCase.createGame(GameType.SLOT_MACHINE, "Slot 1", new BuildingId(BUILDING_ID)))
                .thenReturn(slot);
        when(gameDefinitionLocalRepository.findByGameType(GameType.SLOT_MACHINE))
                .thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"SLOT_MACHINE\",\"name\":\"Slot 1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameId").value("g-slot"))
                .andExpect(jsonPath("$.gameType").value("SLOT_MACHINE"));

        verify(manageGameCatalogUseCase).createGame(GameType.SLOT_MACHINE, "Slot 1", new BuildingId(BUILDING_ID));
    }

    @Test
    void createGame_returns201_forChess_confirmsAvailableGameDefinitionsLocal() throws Exception {
        // Conferma: CHESS era gia replicato localmente prima del fix e continua a
        // funzionare (controllo che il seed locale non rompe il path gia verde).
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        when(gameDefinitionLocalRepository.existsByGameType(GameType.CHESS)).thenReturn(true);
        when(manageGameCatalogUseCase.createGame(GameType.CHESS, "Chess Table 2", new BuildingId(BUILDING_ID)))
                .thenReturn(sampleGame());
        when(gameDefinitionLocalRepository.findByGameType(GameType.CHESS))
                .thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/local/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess Table 2\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.gameType").value("CHESS"));
    }

    @Test
    void updateGame_returns200_whenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);
        Game updated = new Game(new GameId("g1"), GameType.CHESS, "Chess Table Renamed",
                new BuildingId(BUILDING_ID), GameMachineStatus.MAINTENANCE);
        when(manageGameCatalogUseCase.updateGame(new GameId("g1"), "Chess Table Renamed", GameMachineStatus.MAINTENANCE))
                .thenReturn(updated);
        when(gameDefinitionLocalRepository.findByGameType(GameType.CHESS))
                .thenReturn(Optional.empty());

        mvc.perform(put("/api/admin/local/games/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateGameRequestDto("Chess Table Renamed", "MAINTENANCE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("g1"))
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    void updateGame_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(put("/api/admin/local/games/g1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageGameCatalogUseCase);
    }

    @Test
    void deleteGame_returns204_whenAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(true);

        mvc.perform(delete("/api/admin/local/games/g1"))
                .andExpect(status().isNoContent());

        verify(manageGameCatalogUseCase).deleteGame(new GameId("g1"));
    }

    @Test
    void deleteGame_403_whenBuildingNotAuthorized() throws Exception {
        when(authorizationManager.canManageBuilding(any())).thenReturn(false);

        mvc.perform(delete("/api/admin/local/games/g1"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(manageGameCatalogUseCase);
    }
}