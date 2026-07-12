package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.RegisterTournamentParticipantRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
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
 * Slice test for {@link PlayerTournamentRegistrationController} covering
 * {@code POST /api/tournaments/{id}/participants} happy + unauthorized paths.
 * Follows the local {@code standaloneSetup} + Mockito convention already used
 * by {@code PlayerTournamentControllerTest} and
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
 * <p>Spring Security method security ({@code @PreAuthorize("hasRole('PLAYER')")}
 * at class level) is intentionally NOT enforced here: standalone MockMvc does
 * not run the Spring Security filter/method-security chain, so the role check
 * is bypassed by design (mirrored from {@code PlayerTournamentControllerTest}).
 * The {@link CurrentUserService} mock fully controls the authenticated-principal
 * resolution; the {@code buildingId} constructor arg is satisfied with a plain
 * literal ({@code "building-1"}) as in the existing admin controller tests.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code POST /api/tournaments/{id}/participants} 202 on a valid team-registration body;</li>
 *   <li>{@code POST .../participants} 202 on a missing body (individual registration, no team);</li>
 *   <li>{@code POST .../participants} 401 when no authenticated user resolves (currentUserId empty).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlayerTournamentRegistrationControllerTest {

    private static final String BUILDING_ID = "building-1";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock RegisterTournamentParticipantRequestedUseCase registerUseCase;
    @Mock CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlayerTournamentRegistrationController(registerUseCase, currentUserService, BUILDING_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static AdminRequestDto adminRequestDto() {
        return new AdminRequestDto(
                "req-1", "PARTICIPANT_REGISTER_REQUESTED", "u-1", "PLAYER", BUILDING_ID,
                "{}", "PENDING", null, Instant.parse("2026-07-12T10:00:00Z"), null, "req-1");
    }

    @Test
    void register_202_onValidTeamBody() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(registerUseCase.register("t-1", "u-1", "PLAYER", BUILDING_ID,
                "Team Alpha", List.of("u-2", "u-3"))).thenReturn(adminRequestDto());

        mvc.perform(post("/api/tournaments/t-1/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Team Alpha\",\"teamMembers\":[\"u-2\",\"u-3\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("PARTICIPANT_REGISTER_REQUESTED"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(registerUseCase).register("t-1", "u-1", "PLAYER", BUILDING_ID,
                "Team Alpha", List.of("u-2", "u-3"));
    }

    @Test
    void register_202_onMissingBodyForIndividualRegistration() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(registerUseCase.register("t-1", "u-1", "PLAYER", BUILDING_ID, null, null))
                .thenReturn(adminRequestDto());

        mvc.perform(post("/api/tournaments/t-1/participants"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("PARTICIPANT_REGISTER_REQUESTED"));

        verify(registerUseCase).register("t-1", "u-1", "PLAYER", BUILDING_ID, null, null);
    }

    @Test
    void register_401_whenCurrentUserIdEmpty() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(post("/api/tournaments/t-1/participants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"teamName\":\"Team Alpha\",\"teamMembers\":[\"u-2\",\"u-3\"]}"))
                .andExpect(status().isUnauthorized());

        // No authenticated user → the use case must NOT be invoked.
        verifyNoInteractions(registerUseCase);
    }
}