package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Slice test for {@link PlayerStatisticsController} (Verifica 1 — GUI endpoint
 * for the PLAYER "My Stats" view; Verifica 2 — method-level
 * {@code @PreAuthorize("hasRole('PLAYER') or hasRole('PLATFORM_ADMIN')")}).
 *
 * <p>Follows the local {@code standaloneSetup} + Mockito convention; the
 * {@link CurrentUserService} mock controls the authenticated-principal
 * resolution. Negative role-enforcement (403 for LOCAL_ADMIN/GAME_ADMIN) is
 * covered at the architectural level by {@link RoleEnforcementContractTest}
 * (standaloneSetup bypasses the Spring Security method-security chain).</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET /api/players/me/statistics} 200 with no gameType filter returns all;</li>
 *   <li>{@code GET /api/players/me/statistics?gameType=CHESS} 200 returns only the CHESS row;</li>
 *   <li>{@code GET /api/players/me/statistics} 200 empty list when the user is authenticated but not replicated locally;</li>
 *   <li>{@code GET /api/players/me/statistics?gameType=UNKNOWN} 400 on unknown gameType.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlayerStatisticsControllerTest {

    @Mock private GetPlayerStatisticsUseCase useCase;
    @Mock private CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlayerStatisticsController(useCase, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(
                        new com.fasterxml.jackson.databind.ObjectMapper()
                                .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())))
                .build();
    }

    private static PlayerStatisticsDto row(GameType type, int played, int won) {
        return new PlayerStatisticsDto("u1", type, played, won, Instant.parse("2026-07-10T10:00:00Z"));
    }

    @Test
    void getMyStatistics_returnsAllRows_whenNoGameType() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(useCase.getPlayerStatistics(new UserId("u1")))
                .thenReturn(List.of(row(GameType.CHESS, 5, 3), row(GameType.DARTS, 2, 1)));

        mvc.perform(get("/api/players/me/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[1].gameType").value("DARTS"));
    }

    @Test
    void getMyStatistics_filtersByGameType() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(useCase.getPlayerStatistics(new UserId("u1")))
                .thenReturn(List.of(row(GameType.CHESS, 5, 3), row(GameType.DARTS, 2, 1)));

        mvc.perform(get("/api/players/me/statistics").param("gameType", "CHESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"));
    }

    @Test
    void getMyStatistics_returnsEmpty_whenUserNotReplicatedLocally() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(get("/api/players/me/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verifyNoInteractions(useCase);
    }

    @Test
    void getMyStatistics_400_whenGameTypeUnknown() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));

        mvc.perform(get("/api/players/me/statistics").param("gameType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }
}