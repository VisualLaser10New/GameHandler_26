package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gameplatform.local.domain.ports.in.GetAvailableGamesUseCase;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.shared.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class GameControllerTest {

    @Mock private GetAvailableGamesUseCase useCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new GameController(useCase)).build();
    }

    private Game sample() {
        return new Game(new GameId("g1"), GameType.CHESS, "Chess Table",
                new BuildingId("b1"), GameMachineStatus.AVAILABLE);
    }

    @Test
    void getGamesReturnsAll() throws Exception {
        when(useCase.getAll()).thenReturn(List.of(sample()));
        mvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("g1"))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[0].name").value("Chess Table"))
                .andExpect(jsonPath("$[0].buildingId").value("b1"))
                .andExpect(jsonPath("$[0].status").value("AVAILABLE"));
    }

    @Test
    void getAvailableGamesReturnsAvailable() throws Exception {
        when(useCase.getAvailable()).thenReturn(List.of(sample()));
        mvc.perform(get("/api/games/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("g1"));
    }

    @Test
    void emptyListReturnsEmptyArray() throws Exception {
        when(useCase.getAll()).thenReturn(List.of());
        mvc.perform(get("/api/games"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
