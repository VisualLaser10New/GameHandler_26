package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.application.service.TournamentMatchLocalSyncService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

/**
 * Slice test for {@link InternalTournamentController} — delegates
 * {@code PUT /internal/tournaments/matches/sync} to
 * {@link TournamentMatchLocalSyncService#applyEvents}. Follows the local
 * standaloneSetup convention (the {@code InternalApiKeyFilter} is bypassed;
 * {@link GlobalExceptionHandler} is wired for the throw-case).
 */
@ExtendWith(MockitoExtension.class)
class InternalTournamentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock TournamentMatchLocalSyncService tournamentMatchLocalSyncService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalTournamentController(tournamentMatchLocalSyncService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static TournamentMatchScheduledDto sampleDto(String matchId) {
        return new TournamentMatchScheduledDto(
                "evt-" + matchId,
                "TOURNAMENT_MATCH_SCHEDULED",
                matchId,
                "t-1",
                1,
                1,
                "u1",
                "u2",
                GameType.CHESS,
                "game-1",
                "SCHEDULED",
                Instant.parse("2026-07-12T10:00:00Z"),
                "building-1");
    }

    @Test
    void syncTournamentMatches_200_onValidBody() throws Exception {
        TournamentMatchScheduledDto dto = sampleDto("m-1");

        mvc.perform(put("/internal/tournaments/matches/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isOk());

        verify(tournamentMatchLocalSyncService).applyEvents(any());
    }

    @Test
    void syncTournamentMatches_200_onEmptyBody() throws Exception {
        mvc.perform(put("/internal/tournaments/matches/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());

        verify(tournamentMatchLocalSyncService).applyEvents(any());
    }

    @Test
    void syncTournamentMatches_delegatesEvenIfServiceThrows() throws Exception {
        doThrow(new RuntimeException("boom")).when(tournamentMatchLocalSyncService).applyEvents(any());

        mvc.perform(put("/internal/tournaments/matches/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isInternalServerError());

        verify(tournamentMatchLocalSyncService).applyEvents(any());
    }
}