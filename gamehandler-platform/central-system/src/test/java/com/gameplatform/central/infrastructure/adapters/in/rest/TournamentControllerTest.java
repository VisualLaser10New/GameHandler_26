package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.exception.InvalidTournamentStateException;
import com.gameplatform.central.domain.exception.TournamentNotFoundException;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentUseCase;
import com.gameplatform.central.domain.ports.in.GetTournamentStandingsUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.ListTournamentsUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.ScheduleTournamentMatchesUseCase;
import com.gameplatform.central.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.CreateTournamentRequestDto;
import com.gameplatform.shared.dto.TournamentDto;
import com.gameplatform.shared.dto.TournamentMatchDto;
import com.gameplatform.shared.dto.TournamentStandingDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TournamentControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Mock
    private CreateTournamentUseCase createUseCase;

    @Mock
    private OpenTournamentRegistrationUseCase openUseCase;

    @Mock
    private CancelTournamentUseCase cancelUseCase;

    @Mock
    private GetTournamentUseCase getUseCase;

    @Mock
    private ListTournamentsUseCase listTournamentsUseCase;

    @Mock
    private ScheduleTournamentMatchesUseCase scheduleUseCase;

    @Mock
    private GetTournamentStandingsUseCase standingsUseCase;

    @Mock
    private ListTournamentMatchesUseCase matchesUseCase;

    @Mock
    private CurrentUserService currentUserService;

    private Clock clock;
    private TournamentController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        controller = new TournamentController(createUseCase, openUseCase, cancelUseCase,
                getUseCase, listTournamentsUseCase, currentUserService, clock,
                scheduleUseCase, standingsUseCase, matchesUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private TournamentDto tournamentDto(String id, TournamentStatus status) {
        return new TournamentDto(id, "Test Cup", GameType.CHESS, false, 1, status,
                Instant.parse("2026-07-15T10:00:00Z"), null, List.of("b-1", "b-2"), 0);
    }

    @Test
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
    void postCreate_returns400_whenBuildingIdsHasSingleEntry() throws Exception {
        CreateTournamentRequestDto body = new CreateTournamentRequestDto(
                "Test Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-07-15T10:00:00Z"), List.of("b-only"));

        mockMvc.perform(post("/api/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postOpenById_returns200() throws Exception {
        when(openUseCase.open(new TournamentId("t-1")))
                .thenReturn(tournamentDto("t-1", TournamentStatus.OPEN_REGISTRATION));

        mockMvc.perform(post("/api/tournaments/t-1/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN_REGISTRATION"));
    }

    @Test
    void postCancelById_returns200() throws Exception {
        when(cancelUseCase.cancel(new TournamentId("t-1")))
                .thenReturn(tournamentDto("t-1", TournamentStatus.CANCELLED));

        mockMvc.perform(post("/api/tournaments/t-1/cancel"))
                .andExpect(status().isOk());
    }

    @Test
    void getList_returns200_withAllTournaments() throws Exception {
        TournamentDto dto1 = tournamentDto("t-1", TournamentStatus.DRAFT);
        TournamentDto dto2 = tournamentDto("t-2", TournamentStatus.OPEN_REGISTRATION);
        when(listTournamentsUseCase.findAll()).thenReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/tournaments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getListWithStatusParam_returns200_filteredTournaments() throws Exception {
        when(listTournamentsUseCase.findByStatus(TournamentStatus.OPEN_REGISTRATION))
                .thenReturn(List.of(tournamentDto("t-1", TournamentStatus.OPEN_REGISTRATION)));

        mockMvc.perform(get("/api/tournaments").param("status", "OPEN_REGISTRATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getById_returns404_whenTournamentMissing() throws Exception {
        when(getUseCase.getById(new TournamentId("missing"))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tournaments/missing"))
                .andExpect(status().isNotFound());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FASE 5: POST /{id}/schedule, GET /{id}/standings, GET /{id}/matches
    // ──────────────────────────────────────────────────────────────────────────

    @Test
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
    void postSchedule_returns400_whenInvalidTournamentState() throws Exception {
        when(scheduleUseCase.schedule(new TournamentId("t-1")))
                .thenThrow(new InvalidTournamentStateException("Cannot start progress from status COMPLETED"));

        mockMvc.perform(post("/api/tournaments/t-1/schedule"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postSchedule_returns404_whenTournamentNotFound() throws Exception {
        when(scheduleUseCase.schedule(new TournamentId("missing")))
                .thenThrow(new TournamentNotFoundException("Tournament not found: missing"));

        mockMvc.perform(post("/api/tournaments/missing/schedule"))
                .andExpect(status().isNotFound());
    }
}
