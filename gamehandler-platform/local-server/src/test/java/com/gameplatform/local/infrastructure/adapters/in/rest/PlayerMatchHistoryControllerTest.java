package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.domain.ports.in.ListPlayerMatchesUseCase;
import com.gameplatform.local.infrastructure.security.CurrentUserService;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import com.gameplatform.shared.dto.PlayerMatchDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Slice test for {@link PlayerMatchHistoryController} covering
 * {@code GET /api/players/me/matches/history[?gameType=]} happy + error paths.
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
 * resolution; a {@link UsernamePasswordAuthenticationToken} is additionally
 * seeded into {@link SecurityContextHolder} in {@link #setup()} to mirror the
 * real {@code JwtAuthenticationFilter} population and cleared in
 * {@link #tearDown()}.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code GET .../history} 200 with an empty {@code []} body when no authenticated user resolves (currentUserId empty);</li>
 *   <li>{@code GET .../history} 200 with a non-empty list when the use case returns rows for the current user;</li>
 *   <li>{@code GET .../history?gameType=UNKNOWN} 400 on an unknown {@code gameType} (use case never invoked).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PlayerMatchHistoryControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock ListPlayerMatchesUseCase listPlayerMatchesUseCase;
    @Mock CurrentUserService currentUserService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new PlayerMatchHistoryController(listPlayerMatchesUseCase, currentUserService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
        // Mirror JwtAuthenticationFilter: principal = username String, authenticated, ROLE_PLAYER.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", null,
                        List.of(new SimpleGrantedAuthority("ROLE_PLAYER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static PlayerMatchDto sampleMatch() {
        return new PlayerMatchDto(
                "sess-1",
                GameType.CHESS,
                Instant.parse("2026-07-12T10:00:00Z"),
                Instant.parse("2026-07-12T10:10:00Z"),
                600,
                "u-1",
                WinCondition.WIN,
                List.of("u-1", "u-2")
        );
    }

    @Test
    void myMatchHistory_200_emptyArrayWhenCurrentUserIdEmpty() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.empty());

        mvc.perform(get("/api/players/me/matches/history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // No authenticated user → the use case must NOT be queried.
        verifyNoInteractions(listPlayerMatchesUseCase);
    }

    @Test
    void myMatchHistory_200_returnsMatchesForCurrentUser() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));
        when(listPlayerMatchesUseCase.listCompletedMatches(any(), any()))
                .thenReturn(List.of(sampleMatch()));

        mvc.perform(get("/api/players/me/matches/history")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("sess-1"))
                .andExpect(jsonPath("$[0].gameType").value("CHESS"))
                .andExpect(jsonPath("$[0].winnerId").value("u-1"));

        verify(listPlayerMatchesUseCase).listCompletedMatches(eq(new UserId("u-1")), isNull());
    }

    @Test
    void myMatchHistory_400_onUnknownGameTypeQueryParam() throws Exception {
        when(currentUserService.getCurrentUserId()).thenReturn(Optional.of(new UserId("u-1")));

        mvc.perform(get("/api/players/me/matches/history")
                        .param("gameType", "UNKNOWN")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        // The illegal gameType short-circuits BEFORE the use case is invoked.
        verify(listPlayerMatchesUseCase, never()).listCompletedMatches(any(), any());
    }
}