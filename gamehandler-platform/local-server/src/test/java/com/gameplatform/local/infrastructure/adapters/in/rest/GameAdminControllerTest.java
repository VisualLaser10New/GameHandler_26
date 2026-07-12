package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.UpsertGameDefinitionRequestedUseCase;
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
import java.util.Optional;

/**
 * Slice test for {@link GameAdminController} covering
 * {@code POST /api/admin/games} and {@code PUT /api/admin/games/{gameType}}
 * happy + unauthorized paths. Follows the local {@code standaloneSetup} +
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
 * <p>Spring Security method security ({@code @PreAuthorize("hasRole('GAME_ADMIN')")}
 * at class level) is intentionally NOT enforced here: standalone MockMvc does
 * not run the Spring Security filter/method-security chain, so the role check
 * is bypassed by design (mirrored from {@code PlayerTournamentControllerTest}).
 * The {@link CurrentUserService} mock fully controls the authenticated-principal
 * resolution; the {@code buildingId} constructor arg is satisfied with a plain
 * literal ({@code "building-1"}). Bean-validation ({@code @NotNull}/
 * {@code @NotBlank}) is NOT enforced either (no validator wired in
 * standaloneSetup), so the PUT body may legitimately omit {@code gameType}.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code POST /api/admin/games} 202 on a valid upsert body with {@code gameType} in the body;</li>
 *   <li>{@code PUT /api/admin/games/CHESS} 202 on a body omitting {@code gameType} (the path var is used);</li>
 *   <li>{@code POST /api/admin/games} 401 when no authenticated user resolves (currentUserId empty).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GameAdminControllerTest {

    private static final String BUILDING_ID = "building-1";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock UpsertGameDefinitionRequestedUseCase upsertUseCase;
    @Mock CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new GameAdminController(upsertUseCase, currentUserService, BUILDING_ID))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static AdminRequestDto adminRequestDto() {
        return new AdminRequestDto(
                "req-1", "GAME_DEFINITION_UPSERT_REQUESTED", "u-1", "GAME_ADMIN", BUILDING_ID,
                "{}", "PENDING", null, Instant.parse("2026-07-12T10:00:00Z"), null, "req-1");
    }

    @Test
    void createGame_202_onValidPostBody() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(upsertUseCase.upsert(GameType.CHESS, "Chess 2.0", 2, 2, false, null,
                "u-1", "GAME_ADMIN", BUILDING_ID)).thenReturn(adminRequestDto());

        mvc.perform(post("/api/admin/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess 2.0\",\"minPlayers\":2,\"maxPlayers\":2,\"teamAllowed\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("GAME_DEFINITION_UPSERT_REQUESTED"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(upsertUseCase).upsert(GameType.CHESS, "Chess 2.0", 2, 2, false, null,
                "u-1", "GAME_ADMIN", BUILDING_ID);
    }

    @Test
    void updateGame_202_usesPathVarWhenGameTypeOmittedFromBody() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        // body omits gameType → the controller resolves the type from the path variable (CHESS).
        when(upsertUseCase.upsert(GameType.CHESS, "Chess 2.0", 2, 2, false, null,
                "u-1", "GAME_ADMIN", BUILDING_ID)).thenReturn(adminRequestDto());

        mvc.perform(put("/api/admin/games/CHESS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Chess 2.0\",\"minPlayers\":2,\"maxPlayers\":2,\"teamAllowed\":false}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventType").value("GAME_DEFINITION_UPSERT_REQUESTED"));

        verify(upsertUseCase).upsert(GameType.CHESS, "Chess 2.0", 2, 2, false, null,
                "u-1", "GAME_ADMIN", BUILDING_ID);
    }

    @Test
    void createGame_401_whenCurrentUserIdEmpty() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(post("/api/admin/games")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gameType\":\"CHESS\",\"name\":\"Chess 2.0\",\"minPlayers\":2,\"maxPlayers\":2,\"teamAllowed\":false}"))
                .andExpect(status().isUnauthorized());

        // No authenticated user → the use case must NOT be invoked.
        verifyNoInteractions(upsertUseCase);
    }
}