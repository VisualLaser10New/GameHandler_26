package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.gameplatform.central.domain.ports.in.GetGlobalStatisticsUseCase;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.StatisticsDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = StatisticsController.class,
        excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
                type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
                classes = {
                        com.gameplatform.central.infrastructure.config.SecurityConfig.class,
                        com.gameplatform.central.infrastructure.security.JwtAuthenticationFilter.class,
                        com.gameplatform.central.infrastructure.security.InternalApiKeyFilter.class
                }
        )
)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class StatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GetGlobalStatisticsUseCase getGlobalStatisticsUseCase;

    @Test
    void getStatistics_shouldReturn200_whenParametersAreValid() throws Exception {
        StatisticsDto stats = new StatisticsDto(
                "building-1",
                "CHESS",
                java.time.Instant.now().minus(java.time.Duration.ofDays(1)),
                java.time.Instant.now(),
                5,
                120,
                3,
                "{}"
        );
        when(getGlobalStatisticsUseCase.getStatistics(any(), any(), any(), any())).thenReturn(List.of(stats));

        mockMvc.perform(get("/api/statistics")
                        .param("buildingId", "building-1")
                        .param("gameType", "CHESS")
                        .param("start", "2026-01-01")
                        .param("end", "2026-01-07")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].buildingId").value("building-1"))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"));
    }

    @Test
    void getStatistics_shouldReturn400_whenGameTypeIsInvalid() throws Exception {
        mockMvc.perform(get("/api/statistics")
                        .param("gameType", "INVALID_GAME")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unknown game type")));
    }
}
