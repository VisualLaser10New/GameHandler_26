package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.GetLocalServerHealthViewUseCase;
import com.gameplatform.shared.dto.ServerHealthDto;
import com.gameplatform.shared.dto.ServerHealthViewDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

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
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAdminServerController(getLocalServerHealthViewUseCase))
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
}