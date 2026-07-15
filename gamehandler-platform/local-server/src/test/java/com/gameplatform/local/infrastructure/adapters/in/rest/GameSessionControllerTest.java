package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.application.service.GameSessionService;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.User;
import com.gameplatform.local.domain.ports.in.*;
import com.gameplatform.local.domain.ports.out.UserRepository;
import com.gameplatform.shared.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class GameSessionControllerTest {

    @Mock private StartGameSessionUseCase startUseCase;
    @Mock private GameSessionService gameSessionService;
    @Mock private EndGameSessionUseCase endUseCase;
    @Mock private PauseGameSessionUseCase pauseUseCase;
    @Mock private ResumeGameSessionUseCase resumeUseCase;
    @Mock private CreateLobbyUseCase createLobbyUseCase;
    @Mock private JoinLobbyUseCase joinLobbyUseCase;
    @Mock private StartLobbyUseCase startLobbyUseCase;
    @Mock private CancelLobbyUseCase cancelLobbyUseCase;
    @Mock private GetActiveLobbyUseCase getActiveLobbyUseCase;
    @Mock private UserRepository userRepository;
    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        ObjectMapper testMapper = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        testMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        testMapper.addMixIn(com.gameplatform.shared.domain.result.GameResult.class, 
                com.gameplatform.local.infrastructure.config.JacksonConfig.GameResultMixIn.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new GameSessionController(startUseCase, gameSessionService, endUseCase, pauseUseCase, resumeUseCase, createLobbyUseCase, joinLobbyUseCase, startLobbyUseCase, cancelLobbyUseCase, getActiveLobbyUseCase, testMapper, userRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(testMapper))
                .build();
    }

    private GameSession sampleSession() {
        return new GameSession(new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId("b1"), GameStatus.IN_PROGRESS, Instant.parse("2026-02-01T10:00:00Z"),
                null, null, null, null, null, List.of(new UserId("u1")));
    }

    @Test
    void startReturns201AndDto() throws Exception {
        when(gameSessionService.start(any(), any(), any(), any(), any())).thenReturn(sampleSession());
        String body = "{\"gameId\":\"g1\",\"gameType\":\"CHESS\",\"participants\":[\"u1\"],\"reservationId\":\"r1\"}";
        mvc.perform(post("/api/sessions/start").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("s1"))
                .andExpect(jsonPath("$.gameId").value("g1"))
                .andExpect(jsonPath("$.gameType").value("CHESS"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void startWithoutReservationIdPassesNull() throws Exception {
        when(gameSessionService.start(any(), any(), any(), any(), any())).thenReturn(sampleSession());
        String body = "{\"gameId\":\"g1\",\"gameType\":\"CHESS\",\"participants\":[\"u1\"]}";
        mvc.perform(post("/api/sessions/start").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(gameSessionService).start(eq(new GameId("g1")), eq(GameType.CHESS), anyList(), isNull(), any());
    }

    @Test
    void startWithBlankReservationIdPassesNull() throws Exception {
        when(gameSessionService.start(any(), any(), any(), any(), any())).thenReturn(sampleSession());
        String body = "{\"gameId\":\"g1\",\"gameType\":\"CHESS\",\"participants\":[\"u1\"],\"reservationId\":\"  \"}";
        mvc.perform(post("/api/sessions/start").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(gameSessionService).start(any(), any(), anyList(), isNull(), any());
    }

    @Test
    void startWithNullParticipantsPassesEmptyList() throws Exception {
        when(gameSessionService.start(any(), any(), any(), any(), any())).thenReturn(sampleSession());
        String body = "{\"gameId\":\"g1\",\"gameType\":\"CHESS\"}";
        mvc.perform(post("/api/sessions/start").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(gameSessionService).start(any(), any(), argThat(List::isEmpty), any(), any());
    }

    @Test
    void endReturns200() throws Exception {
        String body = "{}";
        mvc.perform(post("/api/sessions/s1/end").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(endUseCase).end(eq(new GameSessionId("s1")), any());
    }

    @Test
    void pauseReturns200() throws Exception {
        mvc.perform(post("/api/sessions/s1/pause"))
                .andExpect(status().isOk());
        verify(pauseUseCase).pause(new GameSessionId("s1"));
    }

    @Test
    void resumeReturns200() throws Exception {
        mvc.perform(post("/api/sessions/s1/resume"))
                .andExpect(status().isOk());
        verify(resumeUseCase).resume(new GameSessionId("s1"));
    }

    @Test
    void getActiveLobbyReturns200WhenLobbyExists() throws Exception {
        GameSession lobby = new GameSession(new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId("b1"), GameStatus.WAITING, Instant.parse("2026-02-01T10:00:00Z"),
                null, null, null, null, null, List.of(new UserId("u1")));
        when(getActiveLobbyUseCase.getActiveLobby(eq(new GameId("g1")))).thenReturn(java.util.Optional.of(lobby));

        mvc.perform(get("/api/sessions/lobby/active").param("gameId", "g1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("s1"))
                .andExpect(jsonPath("$.gameId").value("g1"))
                .andExpect(jsonPath("$.status").value("WAITING"));
    }

    @Test
    void getActiveLobbyReturns404WhenNoLobby() throws Exception {
        when(getActiveLobbyUseCase.getActiveLobby(any())).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/sessions/lobby/active").param("gameId", "g1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActiveLobbyResolvesParticipantUuidsToDisplayUsernames() throws Exception {
        // The server stores lobby participants as canonical UUIDs (see
        // GameSessionServiceParticipantResolutionTest). The lobby-active
        // REST endpoint must resolve them back to display usernames so the
        // joining Game Client Emulator shows names (not UUIDs) in the lobby
        // roster and in the in-match scoreboard.
        UserId creatorUuid = new UserId("11111111-1111-4111-8111-111111111111");
        User creator = new User(creatorUuid, "alice", "hash",
                List.of("PLAYER"), Instant.parse("2026-01-01T00:00:00Z"));
        GameSession lobby = new GameSession(new GameSessionId("s1"), new GameId("g1"),
                GameType.CHESS, new BuildingId("b1"), GameStatus.WAITING,
                Instant.parse("2026-02-01T10:00:00Z"), null, null, null, null, null,
                List.of(creatorUuid));
        when(getActiveLobbyUseCase.getActiveLobby(eq(new GameId("g1")))).thenReturn(java.util.Optional.of(lobby));
        when(userRepository.findById(creatorUuid)).thenReturn(java.util.Optional.of(creator));

        mvc.perform(get("/api/sessions/lobby/active").param("gameId", "g1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0]").value("alice"))
                .andExpect(jsonPath("$.id").value("s1"));
    }

    @Test
    void getActiveLobbyKeepsParticipantWhenUserNotReplicated() throws Exception {
        // An id absent from replicated_users (e.g. a transient username
        // that was not yet canonicalised, or a team-id) is kept verbatim —
        // idempotent resolution that never degrades the historical behaviour.
        UserId rawId = new UserId("unreplicated-name");
        GameSession lobby = new GameSession(new GameSessionId("s1"), new GameId("g1"),
                GameType.CHESS, new BuildingId("b1"), GameStatus.WAITING,
                Instant.parse("2026-02-01T10:00:00Z"), null, null, null, null, null,
                List.of(rawId));
        when(getActiveLobbyUseCase.getActiveLobby(eq(new GameId("g1")))).thenReturn(java.util.Optional.of(lobby));
        when(userRepository.findById(rawId)).thenReturn(java.util.Optional.empty());

        mvc.perform(get("/api/sessions/lobby/active").param("gameId", "g1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0]").value("unreplicated-name"));
    }
}
