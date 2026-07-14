package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.in.GetLocalServerHealthViewUseCase;
import com.gameplatform.local.domain.ports.in.ToggleLocalServerActiveUseCase;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import com.gameplatform.shared.dto.ToggleServerActiveRequestDto;
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
import java.util.Optional;

/**
 * Slice test for {@link PlatformAdminServerController} covering
 * {@code GET /api/admin/servers/health}. Follows the local
 * {@code standaloneSetup} + Mockito convention already used by
 * {@code PlayerTournamentControllerTest} and
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
 * <p>Spring Security method security ({@code @PreAuthorize("hasRole('PLATFORM_ADMIN')")}
 * at class level) is intentionally NOT enforced here: standalone MockMvc does
 * not run the Spring Security filter/method-security chain, so the role check
 * is bypassed by design (mirrored from {@code PlayerTournamentControllerTest}).
 * The controller has no {@link com.gameplatform.local.infrastructure.security.CurrentUserService}
 * dependency, so no authenticated-principal seeding is required.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET /api/admin/servers/health} 200 with {@code $.myBuildingId == "building-1"} and {@code $.myPendingOutboxCount == 3}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminServerControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock GetLocalServerHealthViewUseCase getLocalServerHealthViewUseCase;
    @Mock ToggleLocalServerActiveUseCase toggleLocalServerActiveUseCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAdminServerController(getLocalServerHealthViewUseCase, toggleLocalServerActiveUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static ServerHealthViewDto sampleHealthView() {
        return new ServerHealthViewDto(
                "building-1", true, Instant.parse("2026-07-12T10:00:00Z"), 3L,
                List.of(new ServerHealthDto("building-1", "https://local-1:8081",
                        Instant.parse("2026-07-12T10:00:00Z"), true, 0L)));
    }

    @Test
    void getHealth_200_returnsLocalServerHealthView() throws Exception {
        when(getLocalServerHealthViewUseCase.getHealthView()).thenReturn(sampleHealthView());

        mvc.perform(get("/api/admin/servers/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.myBuildingId").value("building-1"))
                .andExpect(jsonPath("$.myServerActive").value(true))
                .andExpect(jsonPath("$.myPendingOutboxCount").value(3))
                .andExpect(jsonPath("$.registeredServers.length()").value(1))
                .andExpect(jsonPath("$.registeredServers[0].buildingId").value("building-1"));

        verify(getLocalServerHealthViewUseCase).getHealthView();
    }

    // ── Feature 3: PATCH /api/admin/servers/{buildingId}/active ──

    @Test
    void toggleActive_200_updatesProjectionAndReturnsDto() throws Exception {
        RegisteredLocalServerLocal activated = new RegisteredLocalServerLocal(
                new BuildingId("building-3"), "https://local-3:8183",
                Instant.parse("2026-07-12T10:00:00Z"), true, Instant.parse("2026-07-14T09:00:00Z"));
        when(toggleLocalServerActiveUseCase.setActive("building-3", true))
                .thenReturn(Optional.of(activated));

        mvc.perform(patch("/api/admin/servers/building-3/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ToggleServerActiveRequestDto(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildingId").value("building-3"))
                .andExpect(jsonPath("$.baseUrl").value("https://local-3:8183"))
                .andExpect(jsonPath("$.active").value(true));

        verify(toggleLocalServerActiveUseCase).setActive("building-3", true);
    }

    @Test
    void toggleActive_404_whenBuildingUnknown() throws Exception {
        when(toggleLocalServerActiveUseCase.setActive("building-x", false))
                .thenReturn(Optional.empty());

        mvc.perform(patch("/api/admin/servers/building-x/active")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ToggleServerActiveRequestDto(false))))
                .andExpect(status().isNotFound());

        verify(toggleLocalServerActiveUseCase).setActive("building-x", false);
    }
}