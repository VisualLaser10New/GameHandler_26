package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.CreateTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.in.DeleteTournamentRequestedUseCase;
import com.gameplatform.local.domain.ports.in.TournamentLifecycleRequestedUseCase;
import com.gameplatform.local.domain.ports.in.UpdateTournamentRequestedUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
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
 * Slice test for {@link PlatformAdminTournamentController} covering the
 * {@code /api/admin/tournaments} create / lifecycle / update / delete
 * endpoints. Follows the local {@code standaloneSetup} + Mockito convention
 * already used by {@code PlayerTournamentControllerTest} and
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
 * The {@link CurrentUserService} mock fully controls the authenticated-principal
 * resolution; the {@code buildingId} constructor arg is satisfied with a plain
 * literal ({@code "building-1"}). Bean-validation ({@code @NotNull}/
 * {@code @Size}) is NOT enforced either (no validator wired in
 * standaloneSetup), so the bodies do not need to satisfy the DDL-level
 * constraints.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code POST /api/admin/tournaments} 202 on a valid create body;</li>
 *   <li>{@code POST /api/admin/tournaments/{id}/{action}} 202 on {@code open}/{@code cancel}/{@code schedule}, asserting the right {@code eventType} is echoed in the response;</li>
 *   <li>{@code POST /api/admin/tournaments/{id}/whatever} 400 on an unsupported lifecycle action (no use-case interaction);</li>
 *   <li>{@code PUT /api/admin/tournaments/{id}} 202 on a valid update body;</li>
 *   <li>{@code DELETE /api/admin/tournaments/{id}} 202;</li>
 *   <li>{@code POST /api/admin/tournaments} 401 when no authenticated user resolves (currentUserId empty).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlatformAdminTournamentControllerTest {

    private static final String BUILDING_ID = "building-1";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock CreateTournamentRequestedUseCase createUseCase;
    @Mock TournamentLifecycleRequestedUseCase lifecycleUseCase;
    @Mock UpdateTournamentRequestedUseCase updateUseCase;
    @Mock DeleteTournamentRequestedUseCase deleteUseCase;
    @Mock CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlatformAdminTournamentController(createUseCase, lifecycleUseCase,
                                updateUseCase, deleteUseCase, currentUserService, BUILDING_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static AdminRequestDto adminRequestDto(String eventType) {
        return new AdminRequestDto(
                "req-1", eventType, "u-1", "PLATFORM_ADMIN", BUILDING_ID,
                "{}", "PENDING", null, Instant.parse("2026-07-12T10:00:00Z"), null, "req-1");
    }

    private static final String CREATE_BODY =
            "{\"name\":\"Cup\",\"gameType\":\"CHESS\",\"teamBased\":false,\"teamSize\":1,"
                    + "\"startsAt\":\"2026-08-01T10:00:00Z\",\"buildingIds\":[\"b-1\",\"b-2\"]}";
    private static final String UPDATE_BODY =
            "{\"name\":\"Cup v2\",\"startsAt\":\"2026-08-01T10:00:00Z\",\"buildingIds\":[\"b-1\",\"b-2\"]}";

    @Test
    void create_202_onValidBody() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(createUseCase.create("Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1", "b-2"),
                "u-1", "PLATFORM_ADMIN", BUILDING_ID)).thenReturn(adminRequestDto("TOURNAMENT_CREATE_REQUESTED"));

        mvc.perform(post("/api/admin/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_CREATE_REQUESTED"));

        verify(createUseCase).create("Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-08-01T10:00:00Z"), List.of("b-1", "b-2"),
                "u-1", "PLATFORM_ADMIN", BUILDING_ID);
    }

    @Test
    void lifecycle_202_open_emitsOpenRequestedEventType() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(lifecycleUseCase.lifecycle(anyString(), eq("t-1"), eq("u-1"), eq("PLATFORM_ADMIN"), eq(BUILDING_ID)))
                .thenAnswer(inv -> new AdminRequestDto("req-1", (String) inv.getArgument(0),
                        "u-1", "PLATFORM_ADMIN", BUILDING_ID, "{}", "PENDING", null,
                        Instant.parse("2026-07-12T10:00:00Z"), null, "req-1"));

        mvc.perform(post("/api/admin/tournaments/t-1/open"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_OPEN_REQUESTED"));
    }

    @Test
    void lifecycle_202_cancel_emitsCancelRequestedEventType() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(lifecycleUseCase.lifecycle(anyString(), eq("t-1"), eq("u-1"), eq("PLATFORM_ADMIN"), eq(BUILDING_ID)))
                .thenAnswer(inv -> new AdminRequestDto("req-1", (String) inv.getArgument(0),
                        "u-1", "PLATFORM_ADMIN", BUILDING_ID, "{}", "PENDING", null,
                        Instant.parse("2026-07-12T10:00:00Z"), null, "req-1"));

        mvc.perform(post("/api/admin/tournaments/t-1/cancel"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_CANCEL_REQUESTED"));
    }

    @Test
    void lifecycle_202_schedule_emitsScheduleRequestedEventType() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(lifecycleUseCase.lifecycle(anyString(), eq("t-1"), eq("u-1"), eq("PLATFORM_ADMIN"), eq(BUILDING_ID)))
                .thenAnswer(inv -> new AdminRequestDto("req-1", (String) inv.getArgument(0),
                        "u-1", "PLATFORM_ADMIN", BUILDING_ID, "{}", "PENDING", null,
                        Instant.parse("2026-07-12T10:00:00Z"), null, "req-1"));

        mvc.perform(post("/api/admin/tournaments/t-1/schedule"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_SCHEDULE_REQUESTED"));
    }

    @Test
    void lifecycle_400_onUnknownAction() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));

        mvc.perform(post("/api/admin/tournaments/t-1/whatever"))
                .andExpect(status().isBadRequest());

        // The unsupported action short-circuits BEFORE the use case is invoked.
        verifyNoInteractions(lifecycleUseCase);
    }

    @Test
    void update_202_onValidBody() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(updateUseCase.update("t-1", "Cup v2", Instant.parse("2026-08-01T10:00:00Z"),
                List.of("b-1", "b-2"), "u-1", "PLATFORM_ADMIN", BUILDING_ID))
                .thenReturn(adminRequestDto("TOURNAMENT_UPDATE_REQUESTED"));

        mvc.perform(put("/api/admin/tournaments/t-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_UPDATE_REQUESTED"));

        verify(updateUseCase).update("t-1", "Cup v2", Instant.parse("2026-08-01T10:00:00Z"),
                List.of("b-1", "b-2"), "u-1", "PLATFORM_ADMIN", BUILDING_ID);
    }

    @Test
    void delete_202_onExistingTournament() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(deleteUseCase.delete("t-1", "u-1", "PLATFORM_ADMIN", BUILDING_ID))
                .thenReturn(adminRequestDto("TOURNAMENT_DELETE_REQUESTED"));

        mvc.perform(delete("/api/admin/tournaments/t-1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("TOURNAMENT_DELETE_REQUESTED"));

        verify(deleteUseCase).delete("t-1", "u-1", "PLATFORM_ADMIN", BUILDING_ID);
    }

    @Test
    void create_401_whenCurrentUserIdEmpty() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/tournaments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnauthorized());

        // No authenticated user → the use cases must NOT be invoked.
        verifyNoInteractions(createUseCase);
    }
}