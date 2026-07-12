package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.DeleteTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.ScheduleTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.UpdateTournamentUseCase;
import com.gameplatform.central.infrastructure.config.SecurityConfig;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.central.infrastructure.security.InternalApiKeyFilter;
import com.gameplatform.central.infrastructure.security.JwtAuthenticationFilter;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.TournamentDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.UpdateTournamentRequestDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link TournamentController}, migrated from the
 * standalone-setup form to {@link WebMvcTest @WebMvcTest} so that the
 * {@code @PreAuthorize("hasRole('PLATFORM_ADMIN')")} guards on the write
 * endpoints are actually enforced by Spring Security's method security
 * (rather than being skipped as in a bare {@code standaloneSetup}).
 *
 * <p>The JWT/{@code InternalApiKey} filters and the production
 * {@link SecurityConfig} are excluded from the slice; an inline
 * {@link MethodSecurityTestConfig} ({@code @TestConfiguration} +
 * {@link EnableMethodSecurity}) supplies a {@link SecurityFilterChain} that
 * does {@code permitAll()} + CSRF disabled, so request-level access is open
 * and the only gate is method security. {@link WithMockUser @WithMockUser}
 * then impersonates {@code PLATFORM_ADMIN} (write paths) or {@code PLAYER}
 * (read paths / forbidden cases), letting the 403 branch of
 * {@code @PreAuthorize} be exercised end-to-end.
 *
 * <p>Covers the FASE 4 endpoints (create/open/cancel/list/get/schedule/
 * standings/matches) plus the &sect;7.A.1 {@code PUT}/{@code DELETE}
 * endpoints (200/400/404/403 for PUT, 204/404/403 for DELETE).
 */
@WebMvcTest(
        controllers = TournamentController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        SecurityConfig.class,
                        JwtAuthenticationFilter.class,
                        InternalApiKeyFilter.class
                }
        )
)
@Import({GlobalExceptionHandler.class, TournamentControllerTest.MethodSecurityTestConfig.class})
class TournamentControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CreateTournamentUseCase createUseCase;

    @MockBean
    private OpenTournamentRegistrationUseCase openUseCase;

    @MockBean
    private CancelTournamentUseCase cancelUseCase;

    @MockBean
    private GetTournamentUseCase getUseCase;

    @MockBean
    private ListTournamentsUseCase listTournamentsUseCase;

    @MockBean
    private ScheduleTournamentMatchesUseCase scheduleUseCase;

    @MockBean
    private GetTournamentStandingsUseCase standingsUseCase;

    @MockBean
    private ListTournamentMatchesUseCase matchesUseCase;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private UpdateTournamentUseCase updateUseCase;

    @MockBean
    private DeleteTournamentUseCase deleteUseCase;

    private TournamentDto tournamentDto(String id, TournamentStatus status) {
        return new TournamentDto(id, "Test Cup", GameType.CHESS, false, 1, status,
                Instant.parse("2026-07-15T10:00:00Z"), null, List.of("b-1", "b-2"), 0);
    }

    /**
     * Inline test-only security configuration: activates
     * {@link EnableMethodSecurity} so the controller's {@code @PreAuthorize}
     * is enforced, and supplies a {@link SecurityFilterChain} with
     * {@code permitAll()} + CSRF disabled (request-level gate open; method
     * security is the sole gate). Also supplies a deterministic fixed
     * {@link Clock} bean so the {@code create} path's
     * {@code Instant.now(clock)} is stable (a real bean is used instead of a
     * mocked {@code Clock} to avoid an NPE in that path).
     */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /api/tournaments (create)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postCreate_returns200_whenValidRequest() throws Exception {
        CreateTournamentRequestDto body = new CreateTournamentRequestDto(
                "Test Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-07-15T10:00:00Z"), List.of("b-1", "b-2"));
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("admin-1")));
        when(createUseCase.create(any(), any())).thenReturn(tournamentDto("t-1", TournamentStatus.DRAFT));

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t-1"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postCreate_returns400_whenBuildingIdsHasSingleEntry() throws Exception {
        CreateTournamentRequestDto body = new CreateTournamentRequestDto(
                "Test Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-07-15T10:00:00Z"), List.of("b-only"));

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(createUseCase, never()).create(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // POST /{id}/open, POST /{id}/cancel
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postOpenById_returns200() throws Exception {
        when(openUseCase.open(new TournamentId("t-1")))
                .thenReturn(tournamentDto("t-1", TournamentStatus.OPEN_REGISTRATION));

        mockMvc.perform(post("/api/tournaments/t-1/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN_REGISTRATION"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postCancelById_returns200() throws Exception {
        when(cancelUseCase.cancel(new TournamentId("t-1")))
                .thenReturn(tournamentDto("t-1", TournamentStatus.CANCELLED));

        mockMvc.perform(post("/api/tournaments/t-1/cancel"))
                .andExpect(status().isOk());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // GET /api/tournaments, GET /{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLAYER")
    void getList_returns200_withAllTournaments() throws Exception {
        TournamentDto dto1 = tournamentDto("t-1", TournamentStatus.DRAFT);
        TournamentDto dto2 = tournamentDto("t-2", TournamentStatus.OPEN_REGISTRATION);
        when(listTournamentsUseCase.findAll()).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void getListWithStatusParam_returns200_filteredTournaments() throws Exception {
        when(listTournamentsUseCase.findByStatus(TournamentStatus.OPEN_REGISTRATION))
                .thenReturn(List.of(tournamentDto("t-1", TournamentStatus.OPEN_REGISTRATION)));

        mockMvc.perform(get("/api/tournaments").param("status", "OPEN_REGISTRATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void getById_returns404_whenTournamentMissing() throws Exception {
        when(getUseCase.getById(new TournamentId("missing"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tournaments/missing"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FASE 5: POST /{id}/schedule, GET /{id}/standings, GET /{id}/matches
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postSchedule_returns200_andMatchesList() throws Exception {
        TournamentMatchDto m = new TournamentMatchDto("m-1", 1, 1, "PA", "PB",
                null, null, TournamentMatchStatus.SCHEDULED, null, null);
        when(scheduleUseCase.schedule(new TournamentId("t-1"))).thenReturn(List.of(m));

        mockMvc.perform(post("/api/tournaments/t-1/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("m-1"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void getStandings_returns200_andStandingsList() throws Exception {
        TournamentStandingDto s = new TournamentStandingDto("A", "Alice", 2, 0, 6, null);
        when(standingsUseCase.getStandings(new TournamentId("t-1"))).thenReturn(List.of(s));

        mockMvc.perform(get("/api/tournaments/t-1/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].participantId").value("A"))
                .andExpect(jsonPath("$[0].displayName").value("Alice"));
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void getMatches_returns200_andMatchesList() throws Exception {
        TournamentMatchDto m = new TournamentMatchDto("m-1", 1, 1, "PA", "PB",
                "b-1", "g-1", TournamentMatchStatus.SCHEDULED, null, "PA");
        when(matchesUseCase.findByTournament(new TournamentId("t-1"))).thenReturn(List.of(m));

        mockMvc.perform(get("/api/tournaments/t-1/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].winner").value("PA"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postSchedule_returns400_whenInvalidTournamentState() throws Exception {
        when(scheduleUseCase.schedule(new TournamentId("t-1")))
                .thenThrow(new InvalidTournamentStateException("Cannot start progress from status COMPLETED"));

        mockMvc.perform(post("/api/tournaments/t-1/schedule"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void postSchedule_returns404_whenTournamentNotFound() throws Exception {
        when(scheduleUseCase.schedule(new TournamentId("missing")))
                .thenThrow(new TournamentNotFoundException("Tournament not found: missing"));

        mockMvc.perform(post("/api/tournaments/missing/schedule"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // §7.A.1: PUT /api/tournaments/{id} (update)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void putUpdate_returns200_whenValidRequest() throws Exception {
        UpdateTournamentRequestDto body = new UpdateTournamentRequestDto(
                "New", Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1", "b-2"));
        when(updateUseCase.update(any(), any(), any(), any(), any()))
                .thenReturn(tournamentDto("t-1", TournamentStatus.DRAFT));

        mockMvc.perform(put("/api/tournaments/t-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t-1"));
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void putUpdate_returns400_whenBuildingIdsHasSingleEntry() throws Exception {
        UpdateTournamentRequestDto body = new UpdateTournamentRequestDto(
                "New", Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1"));

        mockMvc.perform(put("/api/tournaments/t-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());

        verify(updateUseCase, never()).update(any(), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void putUpdate_returns404_whenTournamentNotFound() throws Exception {
        UpdateTournamentRequestDto body = new UpdateTournamentRequestDto(
                "New", Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1", "b-2"));
        when(updateUseCase.update(any(), any(), any(), any(), any()))
                .thenThrow(new TournamentNotFoundException("Tournament not found: t-1"));

        mockMvc.perform(put("/api/tournaments/t-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void putUpdate_returns403_whenNotAdmin() throws Exception {
        UpdateTournamentRequestDto body = new UpdateTournamentRequestDto(
                "New", Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1", "b-2"));

        mockMvc.perform(put("/api/tournaments/t-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());

        verify(updateUseCase, never()).update(any(), any(), any(), any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // §7.A.1: DELETE /api/tournaments/{id}
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void deleteTournament_returns204_whenExists() throws Exception {
        mockMvc.perform(delete("/api/tournaments/t-1"))
                .andExpect(status().isNoContent());

        verify(deleteUseCase).delete(any(), any());
    }

    @Test
    @WithMockUser(roles = "PLATFORM_ADMIN")
    void deleteTournament_returns404_whenTournamentNotFound() throws Exception {
        doThrow(new TournamentNotFoundException("Tournament not found: t-1"))
                .when(deleteUseCase).delete(any(), any());

        mockMvc.perform(delete("/api/tournaments/t-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "PLAYER")
    void deleteTournament_returns403_whenNotAdmin() throws Exception {
        mockMvc.perform(delete("/api/tournaments/t-1"))
                .andExpect(status().isForbidden());

        verify(deleteUseCase, never()).delete(any(), any());
    }
}
