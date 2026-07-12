package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice IT for {@link PlayerStatisticsController} (FASE 3, PIANO &sect;2.4),
 * mirroring the {@code GameAdminControllerTest} style: a {@code standaloneSetup}
 * MockMvc with a mocked {@link GetPlayerStatisticsUseCase} and a mocked
 * {@link CurrentUserService} (so no Spring Security filter chain / method
 * security is active &mdash; authorisation is asserted at the use-case /
 * {@code @PreAuthorize} level by the Central {@code SecurityConfig} in
 * production). The standalone Jackson converter is configured with
 * {@link JavaTimeModule} so the {@link Instant} field on {@link PlayerStatisticsDto}
 * serialises the same way the production ObjectMapper does.
 */
@ExtendWith(MockitoExtension.class)
class PlayerStatisticsControllerTest {

    private static final Instant T1 = Instant.parse("2026-07-12T09:30:00Z");
    private static final UserId USER = new UserId("user-1");

    @Mock
    private GetPlayerStatisticsUseCase getPlayerStatisticsUseCase;

    @Mock
    private CurrentUserService currentUserService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PlayerStatisticsController controller =
                new PlayerStatisticsController(getPlayerStatisticsUseCase, currentUserService);
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    private PlayerStatisticsDto dto(UserId userId, GameType gameType, int played, int won) {
        return new PlayerStatisticsDto(userId.value(), gameType, played, won, T1);
    }

    @Test
    void getMyStatistics_returnsDtoList() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));
        when(getPlayerStatisticsUseCase.getStatistics(USER, null))
                .thenReturn(List.of(dto(USER, GameType.CHESS, 3, 2)));

        mockMvc.perform(get("/api/players/me/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(USER.value()))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[0].matchesPlayed").value(3))
                .andExpect(jsonPath("$[0].matchesWon").value(2))
                .andExpect(jsonPath("$[0].lastPlayedAt").value("2026-07-12T09:30:00Z"));
    }

    @Test
    void getMyStatistics_passesGameTypeFilter() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));
        when(getPlayerStatisticsUseCase.getStatistics(USER, GameType.CHESS))
                .thenReturn(List.of(dto(USER, GameType.CHESS, 3, 2)));

        mockMvc.perform(get("/api/players/me/statistics").param("gameType", "CHESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameType").value("CHESS"));
    }

    @Test
    void getMyStatistics_returns403_whenCurrentUserNotResolvable() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/players/me/statistics"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getMyStatistics_returns400_whenGameTypeInvalid() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));

        mockMvc.perform(get("/api/players/me/statistics").param("gameType", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unknown game type")));
    }

    @Test
    void getUserStatistics_self_returns200() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));
        when(currentUserService.hasRole("PLATFORM_ADMIN")).thenReturn(false);
        when(getPlayerStatisticsUseCase.getStatistics(eq(USER), eq(null)))
                .thenReturn(List.of(dto(USER, GameType.DARTS, 1, 1)));

        mockMvc.perform(get("/api/players/{userId}/statistics", USER.value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(USER.value()))
                .andExpect(jsonPath("$[0].gameType").value("DARTS"));
    }

    @Test
    void getUserStatistics_otherUser_returns403_whenNotAdmin() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));
        when(currentUserService.hasRole("PLATFORM_ADMIN")).thenReturn(false);

        mockMvc.perform(get("/api/players/{userId}/statistics", "other-user"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserStatistics_otherUser_returns200_whenPlatformAdmin() throws Exception {
        UserId other = new UserId("other-user");
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(USER));
        when(currentUserService.hasRole("PLATFORM_ADMIN")).thenReturn(true);
        when(getPlayerStatisticsUseCase.getStatistics(eq(other), eq(null)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/players/{userId}/statistics", "other-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}