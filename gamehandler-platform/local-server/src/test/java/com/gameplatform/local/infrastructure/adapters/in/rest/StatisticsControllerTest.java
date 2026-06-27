package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.ports.in.GetStatisticsUseCase;
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

@ExtendWith(MockitoExtension.class)
class StatisticsControllerTest {

    @Mock private GetStatisticsUseCase useCase;
    private MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new StatisticsController(useCase, objectMapper))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getStatsReturnsStatistics() throws Exception {
        com.gameplatform.local.domain.model.LocalStatistics stats =
                new com.gameplatform.local.domain.model.LocalStatistics(GameType.CHESS, 5, 120.0, 3, java.util.Map.of());
        when(useCase.getStatistics(GameType.CHESS)).thenReturn(stats);
        mvc.perform(get("/api/statistics").param("gameType", "chess"))
                .andExpect(status().isOk());
    }

    @Test
    void getStatsWithInvalidGameTypeThrowsIllegalArg500() throws Exception {
        mvc.perform(get("/api/statistics").param("gameType", "NOT_A_GAME"))
                .andExpect(status().isInternalServerError());
        verifyNoInteractions(useCase);
    }

    @Test
    void getActiveSessionsReturnsDtos() throws Exception {
        GameSession session = new GameSession(new GameSessionId("s1"), new GameId("g1"), GameType.CHESS,
                new BuildingId("b1"), GameStatus.IN_PROGRESS, Instant.parse("2026-02-01T10:00:00Z"),
                null, null, null, null, null, List.of());
        when(useCase.getActiveSessions()).thenReturn(List.of(session));
        mvc.perform(get("/api/sessions/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("s1"))
                .andExpect(jsonPath("$[0].gameId").value("g1"));
    }
}
