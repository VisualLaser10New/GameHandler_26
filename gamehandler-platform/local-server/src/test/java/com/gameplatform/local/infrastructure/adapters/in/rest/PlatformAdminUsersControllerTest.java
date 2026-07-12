package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.ListUsersDirectoryUseCase;
import com.gameplatform.shared.dto.UsersDirectoryDto;
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
 * Slice test for {@link PlatformAdminUsersController} covering
 * {@code GET /api/admin/users}. Follows the local {@code standaloneSetup} +
 * Mockito convention already used by {@code PlayerTournamentControllerTest}
 * and {@link InternalTournamentSummaryControllerTest}.
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
 *   <li>{@code GET /api/admin/users} 200 with an empty {@code []} body;</li>
 *   <li>{@code GET /api/admin/users} 200 with a {@code [dto]} body, asserting {@code $[0].userId == "u-1"} and {@code $[0].roles[0] == "PLAYER"}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminUsersControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock ListUsersDirectoryUseCase listUsersDirectoryUseCase;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAdminUsersController(listUsersDirectoryUseCase))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static UsersDirectoryDto sampleUser() {
        return new UsersDirectoryDto(
                "u-1", "alice", "alice@example.com", List.of("PLAYER"),
                Instant.parse("2026-07-12T10:00:00Z"));
    }

    @Test
    void listUsers_200_emptyArrayWhenNoUsers() throws Exception {
        when(listUsersDirectoryUseCase.listAllUsers()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(listUsersDirectoryUseCase).listAllUsers();
    }

    @Test
    void listUsers_200_returnsUsersDirectory() throws Exception {
        when(listUsersDirectoryUseCase.listAllUsers()).thenReturn(List.of(sampleUser()));

        mvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value("u-1"))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[0].roles[0]").value("PLAYER"));

        verify(listUsersDirectoryUseCase).listAllUsers();
    }
}