package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.application.service.GetTournamentDetailService;
import com.gameplatform.local.application.service.ListTournamentSummariesService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentDetailDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import com.gameplatform.shared.dto.TournamentSummaryDto;
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
 * Slice test for {@link PlayerTournamentSummaryController} covering the
 * {@code /api/tournaments} read endpoints (list, detail, standings, matches,
 * participants). Follows the local {@code standaloneSetup} + Mockito
 * convention already used by {@code PlayerTournamentControllerTest} and
 * {@link InternalTournamentSummaryControllerTest}.
 *
 * <p><b>Why a Mockito standaloneSetup slice and not a full
 * {@code @SpringBootTest} (H2) IT:</b> the local-server's
 * {@code @SpringBootApplication} eagerly instantiates the MQTT client
 * ({@code MqttConfig.mqttClient}, connect to {@code tcp://localhost:1883})
 * during context refresh, which fails in CI/dev (no broker). This is the same
 * documented constraint already noted in {@code AdminLocalControllerIT} and
 * {@code UserRepositoryAdapterOrderingGuardIT} javadocs, which is why every
 * existing local-server controller test uses the same
 * {@code MockMvcBuilders.standaloneSetup} + Mockito convention.</p>
 *
 * <p>The controller intentionally declares no class-level {@code @PreAuthorize}
 * (the spec requires {@code isAuthenticated()}, already the catch-all in
 * {@code SecurityConfig}), so no Spring Security method security is exercised
 * here. {@code CurrentUserService} is not a dependency of this controller, so
 * no {@link SecurityContextHolder} seeding / clearing is required.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET /api/tournaments?status=DRAFT} 200 with a status filter;</li>
 *   <li>{@code GET /api/tournaments} 200 without a filter;</li>
 *   <li>{@code GET /api/tournaments?status=NOPE} 400 on an unknown status;</li>
 *   <li>{@code GET /api/tournaments/t-1} 200 when the detail is present, 404 when absent;</li>
 *   <li>{@code GET /api/tournaments/t-1/standings} 200 + {@code $[0].participantId == "p-a"};</li>
 *   <li>{@code GET /api/tournaments/t-1/matches} 200;</li>
 *   <li>{@code GET /api/tournaments/t-1/participants} 200 + {@code $[0].participantId == "p-a"}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlayerTournamentSummaryControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock ListTournamentSummariesService listTournamentSummariesService;
    @Mock GetTournamentDetailService getTournamentDetailService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlayerTournamentSummaryController(listTournamentSummariesService,
                                getTournamentDetailService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static TournamentSummaryDto summaryDto() {
        return new TournamentSummaryDto(
                "t-1", "Test Cup", GameType.CHESS, false, 1, TournamentStatus.DRAFT,
                Instant.parse("2026-08-01T10:00:00Z"), null, List.of("b-1", "b-2"), 0,
                Instant.parse("2026-07-12T10:00:00Z"));
    }

    private static TournamentDetailDto detailDto() {
        return new TournamentDetailDto(
                summaryDto(),
                List.of(new TournamentStandingDto("p-a", "Alice", 3, 1, 9, 1)),
                List.of(new TournamentMatchDto("m-1", 1, 1, "p-a", "p-b", null, null,
                        TournamentMatchStatus.SCHEDULED, Instant.parse("2026-08-05T10:00:00Z"), null)),
                List.of(new TournamentParticipantViewDto("p-a", false, "Alice",
                        Instant.parse("2026-07-12T10:00:00Z"))));
    }

    @Test
    void listTournaments_200_withStatusFilter() throws Exception {
        when(listTournamentSummariesService.listSummaries(TournamentStatus.DRAFT))
                .thenReturn(List.of(summaryDto()));

        mvc.perform(get("/api/tournaments").param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tournamentId").value("t-1"))
                .andExpect(jsonPath("$[0].status").value("DRAFT"));

        verify(listTournamentSummariesService).listSummaries(TournamentStatus.DRAFT);
    }

    @Test
    void listTournaments_200_withoutFilter() throws Exception {
        when(listTournamentSummariesService.listSummaries(isNull()))
                .thenReturn(List.of(summaryDto()));

        mvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        verify(listTournamentSummariesService).listSummaries(isNull());
    }

    @Test
    void listTournaments_400_onUnknownStatusFilter() throws Exception {
        mvc.perform(get("/api/tournaments").param("status", "NOPE"))
                .andExpect(status().isBadRequest());

        // The illegal status short-circuits BEFORE the service is invoked.
        verifyNoInteractions(listTournamentSummariesService);
    }

    @Test
    void getTournament_200_whenPresent() throws Exception {
        when(getTournamentDetailService.getDetail("t-1")).thenReturn(Optional.of(detailDto()));

        mvc.perform(get("/api/tournaments/t-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.tournamentId").value("t-1"))
                .andExpect(jsonPath("$.summary.name").value("Test Cup"));
    }

    @Test
    void getTournament_404_whenAbsent() throws Exception {
        when(getTournamentDetailService.getDetail("t-404")).thenReturn(Optional.empty());

        mvc.perform(get("/api/tournaments/t-404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getTournamentStandings_200_projectsStandingsList() throws Exception {
        when(getTournamentDetailService.getDetail("t-1")).thenReturn(Optional.of(detailDto()));

        mvc.perform(get("/api/tournaments/t-1/standings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].participantId").value("p-a"))
                .andExpect(jsonPath("$[0].displayName").value("Alice"));
    }

    @Test
    void getTournamentMatches_200_projectsMatchesList() throws Exception {
        when(getTournamentDetailService.getDetail("t-1")).thenReturn(Optional.of(detailDto()));

        mvc.perform(get("/api/tournaments/t-1/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("m-1"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"));
    }

    @Test
    void getTournamentParticipants_200_projectsParticipantsList() throws Exception {
        when(getTournamentDetailService.getDetail("t-1")).thenReturn(Optional.of(detailDto()));

        mvc.perform(get("/api/tournaments/t-1/participants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].participantId").value("p-a"))
                .andExpect(jsonPath("$[0].displayName").value("Alice"));
    }

    @Test
    void getTournamentStandings_404_whenAbsent() throws Exception {
        when(getTournamentDetailService.getDetail("t-404")).thenReturn(Optional.empty());

        mvc.perform(get("/api/tournaments/t-404/standings"))
                .andExpect(status().isNotFound());
    }
}