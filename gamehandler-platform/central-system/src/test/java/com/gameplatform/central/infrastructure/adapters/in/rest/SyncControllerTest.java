package com.gameplatform.central.infrastructure.adapters.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = SyncController.class,
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
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReceiveSyncDataUseCase receiveSyncDataUseCase;

    @MockBean
    private LocalServerRegistryPort localServerRegistryPort;

    @Test
    void receiveSync_shouldReturn200_whenPayloadIsValid() throws Exception {
        SyncPayloadDto payload = new SyncPayloadDto("building-123", Collections.emptyList());

        mockMvc.perform(post("/internal/sync/receive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk());

        verify(receiveSyncDataUseCase).receiveSyncPayload(any(SyncPayloadDto.class));
    }

    @Test
    void registerServer_shouldReturn200_whenPayloadIsValid() throws Exception {
        Map<String, String> body = Map.of(
                "buildingId", "building-123",
                "baseUrl", "http://localhost:8181"
        );

        mockMvc.perform(post("/internal/servers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        verify(localServerRegistryPort).register(any());
    }

    @Test
    void registerServer_shouldReturn400_whenBuildingIdIsBlank() throws Exception {
        Map<String, String> body = Map.of(
                "buildingId", "",
                "baseUrl", "http://localhost:8181"
        );

        mockMvc.perform(post("/internal/servers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerServer_shouldReturn400_whenBaseUrlIsBlank() throws Exception {
        Map<String, String> body = Map.of(
                "buildingId", "building-123",
                "baseUrl", ""
        );

        mockMvc.perform(post("/internal/servers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
