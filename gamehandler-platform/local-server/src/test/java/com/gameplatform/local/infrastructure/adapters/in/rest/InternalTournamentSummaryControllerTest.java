package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.application.service.TournamentSummarySyncService;
import com.gameplatform.local.infrastructure.security.InternalApiKeyFilter;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentStatus;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

/**
 * Slice test for {@link InternalTournamentSummaryController}. Mirrors
 * {@link InternalTournamentControllerTest} (FASE 6) with one explicit
 * deviation: the {@link InternalApiKeyFilter} is wired into MockMvc via
 * {@code addFilter} so the 401 contract on missing/invalid
 * {@code X-Internal-Api-Key} header is verified end-to-end through the filter
 * chain (the FASE 6 sibling bypasses the filter with bare standaloneSetup).
 *
 * <p><b>Why a Mockito standaloneSetup slice and not a full
 * {@code @SpringBootTest} (H2) IT:</b> the local-server's
 * {@code @SpringBootApplication} eagerly instantiates the MQTT client
 * ({@code MqttConfig.mqttClient}, connect to {@code tcp://localhost:1883})
 * during context refresh, which fails in CI/dev (no broker). This is the same
 * documented constraint already noted in {@code AdminLocalControllerIT} and
 * {@code UserRepositoryAdapterOrderingGuardIT} javadocs, which is why every
 * existing local-server controller test uses the same
 * {@code MockMvcBuilders.standaloneSetup} + Mockito convention. The
 * {@code addFilter(new InternalApiKeyFilter(...))} trick closes the gap on the
 * 401-without-API-key requirement without requiring a full Spring context.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@code PUT /internal/tournaments/summaries/sync} 200 on a valid body with correct {@code X-Internal-Api-Key};</li>
 *   <li>{@code PUT .../sync} 401 on a missing {@code X-Internal-Api-Key} (no delegation to the sync service);</li>
 *   <li>{@code PUT .../sync} 401 on a wrong {@code X-Internal-Api-Key};</li>
 *   <li>Idempotency at the HTTP layer: the same body is accepted twice (the upsert idempotency lives in the sync service, asserted in {@code TournamentSummarySyncServiceTest}).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InternalTournamentSummaryControllerTest {

    private static final String INTERNAL_API_KEY = "test-internal-key-123";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock TournamentSummarySyncService tournamentSummarySyncService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalTournamentSummaryController(tournamentSummarySyncService))
                .addFilter(new InternalApiKeyFilter(INTERNAL_API_KEY, new MockEnvironment()))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static TournamentSummaryEventDto sampleDto(String tournamentId) {
        return new TournamentSummaryEventDto(
                "evt-" + tournamentId,
                "TOURNAMENT_SUMMARY_UPSERTED",
                tournamentId,
                "Test Cup",
                GameType.CHESS,
                false,
                1,
                TournamentStatus.DRAFT,
                Instant.parse("2026-08-01T10:00:00Z"),
                null,
                List.of("b-1", "b-2"),
                0,
                Instant.parse("2026-07-12T10:00:00Z"),
                false,
                null
        );
    }

    @Test
    void syncTournamentSummaries_200_whenApiKeyValidAndBodyIsValid() throws Exception {
        TournamentSummaryEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isOk());

        verify(tournamentSummarySyncService).applyEvents(any());
    }

    @Test
    void syncTournamentSummaries_200_onEmptyBody() throws Exception {
        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());

        verify(tournamentSummarySyncService).applyEvents(any());
    }

    @Test
    void syncTournamentSummaries_401_whenApiKeyMissing() throws Exception {
        TournamentSummaryEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isUnauthorized());

        // The filter must short-circuit BEFORE the controller — the service must NOT be invoked.
        verifyNoInteractions(tournamentSummarySyncService);
    }

    @Test
    void syncTournamentSummaries_401_whenApiKeyWrong() throws Exception {
        TournamentSummaryEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tournamentSummarySyncService);
    }

    @Test
    void syncTournamentSummaries_isIdempotentAtHttpLayer_sameBodyAcceptedTwice() throws Exception {
        TournamentSummaryEventDto dto = sampleDto("t-1");
        String body = objectMapper.writeValueAsString(List.of(dto));

        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mvc.perform(put("/internal/tournaments/summaries/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // The HTTP layer (controller + filter) is stateless — both calls go through.
        verify(tournamentSummarySyncService, times(2)).applyEvents(any());
        // The upsert-by-PK idempotency itself is unit-tested in TournamentSummarySyncServiceTest.
    }
}
