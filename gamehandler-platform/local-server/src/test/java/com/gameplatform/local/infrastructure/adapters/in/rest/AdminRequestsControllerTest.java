package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.ListAdminRequestsUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.AdminRequestDto;
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
import java.util.Optional;

/**
 * Slice test for {@link AdminRequestsController} covering the self-service
 * {@code GET /api/admin/requests} and {@code GET /api/admin/requests/{requestId}}
 * poll endpoints. Follows the local {@code standaloneSetup} + Mockito
 * convention already used by {@code PlayerTournamentControllerTest} and
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
 * <p>The controller declares no class-level {@code @PreAuthorize} (the spec
 * requires {@code isAuthenticated()}), so no Spring Security method security is
 * exercised here. The {@code actingUserId == principal} cross-user read filter
 * is enforced in-controller via {@link CurrentUserService}, fully controlled by
 * the mock. {@code CurrentUserService} is not the real component here, so no
 * {@link SecurityContextHolder} seeding / clearing is required.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET /api/admin/requests} 200 with an empty {@code []} body;</li>
 *   <li>{@code GET /api/admin/requests} 200 with a {@code [dto]} body for the acting user;</li>
 *   <li>{@code GET /api/admin/requests/{requestId}} 200 with the owned request dto;</li>
 *   <li>{@code GET /api/admin/requests/{requestId}} 404 when the dto's {@code actingUserId} differs from the principal (cross-user filter);</li>
 *   <li>{@code GET /api/admin/requests} 401 when no authenticated user resolves (currentUserId empty).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AdminRequestsControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock ListAdminRequestsUseCase listAdminRequestsUseCase;
    @Mock CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new AdminRequestsController(listAdminRequestsUseCase, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static AdminRequestDto sampleDto(String requestId, String actingUserId) {
        return new AdminRequestDto(
                requestId, "PARTICIPANT_REGISTER_REQUESTED", actingUserId, "PLAYER", "building-1",
                "{}", "PENDING", null, Instant.parse("2026-07-12T10:00:00Z"), null, requestId);
    }

    @Test
    void listMyRequests_200_emptyArrayWhenNoRequests() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(listAdminRequestsUseCase.listByActingUser("u-1")).thenReturn(List.of());

        mvc.perform(get("/api/admin/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(listAdminRequestsUseCase).listByActingUser("u-1");
    }

    @Test
    void listMyRequests_200_returnsOwnRequests() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(listAdminRequestsUseCase.listByActingUser("u-1"))
                .thenReturn(List.of(sampleDto("req-1", "u-1")));

        mvc.perform(get("/api/admin/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value("req-1"))
                .andExpect(jsonPath("$[0].actingUserId").value("u-1"));

        verify(listAdminRequestsUseCase).listByActingUser("u-1");
    }

    @Test
    void getMyRequest_200_returnsOwnedRequest() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(listAdminRequestsUseCase.findByRequestId("req-1"))
                .thenReturn(Optional.of(sampleDto("req-1", "u-1")));

        mvc.perform(get("/api/admin/requests/req-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.actingUserId").value("u-1"));

        verify(listAdminRequestsUseCase).findByRequestId("req-1");
    }

    @Test
    void getMyRequest_404_whenDtoActingUserIdDiffersFromPrincipal() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        // The use case returns a dto owned by another user → the in-controller cross-user filter yields 404.
        when(listAdminRequestsUseCase.findByRequestId("req-1"))
                .thenReturn(Optional.of(sampleDto("req-1", "u-other")));

        mvc.perform(get("/api/admin/requests/req-1"))
                .andExpect(status().isNotFound());

        verify(listAdminRequestsUseCase).findByRequestId("req-1");
    }

    @Test
    void listMyRequests_401_whenCurrentUserIdEmpty() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(get("/api/admin/requests"))
                .andExpect(status().isUnauthorized());

        // No authenticated user → the use case must NOT be invoked.
        verifyNoInteractions(listAdminRequestsUseCase);
    }
}