package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Slice test for {@link PlayerTournamentController} covering
 * {@code GET /api/players/tournaments/me/matches} and
 * {@code POST /api/players/tournaments/matches/{matchId}/start} happy + error
 * paths. Follows the local standaloneSetup convention; the 4th controller ctor
 * param (an {@link ObjectMapper}, per the local subagent report) is satisfied
 * with a real instance, and {@link GlobalExceptionHandler} is wired so the
 * tournament-specific exceptions map to 404/409.
 */
@ExtendWith(MockitoExtension.class)
class PlayerTournamentControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock TournamentMatchLocalRepository tournamentMatchLocalRepository;
    @Mock CurrentUserService currentUserService;
    @Mock GameSessionService gameSessionService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlayerTournamentController(tournamentMatchLocalRepository,
                                currentUserService, gameSessionService, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static TournamentMatchLocal scheduledMatch(String matchId, String participantA, String participantB, String gameId) {
        return new TournamentMatchLocal(new TournamentMatchId(matchId), new TournamentId("t-1"),
                1, 1, participantA, participantB, GameType.CHESS, gameId,
                TournamentMatchStatus.SCHEDULED, Instant.parse("2026-07-12T10:00:00Z"));
    }

    @Test
    void myMatches_200_returnsScheduledMatchesForCurrentUser() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(tournamentMatchLocalRepository.findScheduledByParticipant("u1"))
                .thenReturn(List.of(
                        scheduledMatch("m-1", "u1", "u2", "game-1"),
                        scheduledMatch("m-2", "u3", "u1", "game-2")));

        mvc.perform(get("/api/players/tournaments/me/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("m-1"))
                .andExpect(jsonPath("$[1].id").value("m-2"));
    }

    @Test
    void myMatches_200_emptyWhenNoUserId() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(get("/api/players/tournaments/me/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(tournamentMatchLocalRepository, never()).findScheduledByParticipant(any());
    }

    @Test
    void myMatches_200_emptyWhenNoScheduledMatches() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u1")));
        when(tournamentMatchLocalRepository.findScheduledByParticipant("u1")).thenReturn(List.of());

        mvc.perform(get("/api/players/tournaments/me/matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void startMatch_404_whenMatchNotFound() throws Exception {
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-404"))))
                .thenReturn(Optional.empty());

        mvc.perform(post("/api/players/tournaments/matches/m-404/start"))
                .andExpect(status().isNotFound());

        verify(gameSessionService, never()).start(any(GameId.class), any(GameType.class), any(), any(), any());
    }

    @Test
    void startMatch_409_whenMatchNotScheduled() throws Exception {
        TournamentMatchLocal notScheduled = new TournamentMatchLocal(
                new TournamentMatchId("m-1"), new TournamentId("t-1"), 1, 1,
                "u1", "u2", GameType.CHESS, "game-1",
                TournamentMatchStatus.COMPLETED, Instant.parse("2026-07-12T10:00:00Z"));
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(notScheduled));

        mvc.perform(post("/api/players/tournaments/matches/m-1/start"))
                .andExpect(status().isConflict());

        verify(gameSessionService, never()).start(any(GameId.class), any(GameType.class), any(), any(), any());
    }

    @Test
    void startMatch_200_delegatesToGameSessionService() throws Exception {
        when(tournamentMatchLocalRepository.findById(eq(new TournamentMatchId("m-1"))))
                .thenReturn(Optional.of(scheduledMatch("m-1", "u1", "u2", "game-1")));
        GameSession started = new GameSession(new GameSessionId("s-1"), new GameId("game-1"),
                GameType.CHESS, new BuildingId("building-1"), GameStatus.IN_PROGRESS,
                Instant.parse("2026-07-12T10:00:00Z"), null, null, null, null, null,
                List.of(new UserId("u1"), new UserId("u2")));
        when(gameSessionService.start(any(GameId.class), any(GameType.class), any(), any(), any()))
                .thenReturn(started);

        mvc.perform(post("/api/players/tournaments/matches/m-1/start"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s-1"))
                .andExpect(jsonPath("$.gameId").value("game-1"));

        verify(gameSessionService).start(any(GameId.class), any(GameType.class), any(), isNull(),
                eq(new TournamentMatchId("m-1")));
    }
}