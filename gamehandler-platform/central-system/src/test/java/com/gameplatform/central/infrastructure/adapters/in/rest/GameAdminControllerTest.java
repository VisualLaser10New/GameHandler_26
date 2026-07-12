package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.ports.in.ListGameDefinitionsUseCase;
import com.gameplatform.central.domain.ports.in.UpsertGameDefinitionUseCase;
import com.gameplatform.shared.domain.model.GameType;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GameAdminControllerTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-12T10:00:00Z");

    @Mock
    private UpsertGameDefinitionUseCase upsertUseCase;

    @Mock
    private ListGameDefinitionsUseCase listUseCase;

    private Clock clock;
    private GameAdminController controller;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        controller = new GameAdminController(upsertUseCase, listUseCase, clock);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    private String chessRequestBody() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameType", "CHESS");
        body.put("name", "Scacchi");
        body.put("minPlayers", 2);
        body.put("maxPlayers", 2);
        body.put("teamAllowed", false);
        return objectMapper.writeValueAsString(body);
    }

    private GameDefinition savedChess() {
        return new GameDefinition(
                GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
    }

    @Test
    void postDefinitions_upsertsAndReturns200() throws Exception {
        when(upsertUseCase.upsert(any())).thenReturn(savedChess());

        mockMvc.perform(post("/api/admin/games/definitions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chessRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("CHESS"))
                .andExpect(jsonPath("$.name").value("Scacchi"))
                .andExpect(jsonPath("$.minPlayers").value(2))
                .andExpect(jsonPath("$.maxPlayers").value(2))
                .andExpect(jsonPath("$.teamAllowed").value(false));

        verify(upsertUseCase).upsert(argThat(def ->
                def != null
                        && def.getGameType() == GameType.CHESS
                        && "Scacchi".equals(def.getName())
                        && def.getMinPlayers() == 2
                        && def.getMaxPlayers() == 2
                        && !def.isTeamAllowed()
                        && FIXED_NOW.equals(def.getCreatedAt())
                        && FIXED_NOW.equals(def.getUpdatedAt())));
    }

    @Test
    void putDefinitions_validatesAndUpdatesExisting() throws Exception {
        when(upsertUseCase.upsert(any())).thenReturn(savedChess());

        mockMvc.perform(put("/api/admin/games/definitions/CHESS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(chessRequestBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameType").value("CHESS"))
                .andExpect(jsonPath("$.name").value("Scacchi"))
                .andExpect(jsonPath("$.minPlayers").value(2))
                .andExpect(jsonPath("$.maxPlayers").value(2))
                .andExpect(jsonPath("$.teamAllowed").value(false));
    }

    @Test
    void putDefinitions_400WhenPathGameTypeMismatchesBody() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameType", "FOOSBALL");
        body.put("name", "Calcetto");
        body.put("minPlayers", 2);
        body.put("maxPlayers", 4);
        body.put("teamAllowed", true);

        mockMvc.perform(put("/api/admin/games/definitions/CHESS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("must match body gameType")));
    }

    @Test
    void getDefinitions_returnsListOfDtos() throws Exception {
        GameDefinition defChess = new GameDefinition(
                GameType.CHESS, "Scacchi", 2, 2, false, null, FIXED_NOW, FIXED_NOW);
        GameDefinition defFoosball = new GameDefinition(
                GameType.FOOSBALL, "Calcetto", 2, 4, true, null, FIXED_NOW, FIXED_NOW);
        when(listUseCase.findAll()).thenReturn(List.of(defChess, defFoosball));

        mockMvc.perform(get("/api/admin/games/definitions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[1].gameType").value("FOOSBALL"));
    }
}