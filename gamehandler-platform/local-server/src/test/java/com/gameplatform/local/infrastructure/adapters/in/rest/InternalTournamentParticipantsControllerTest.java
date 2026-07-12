package com.gameplatform.local.infrastructure.adapters.in.rest;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.local.application.service.TournamentParticipantsLocalSyncService;
import com.gameplatform.local.infrastructure.security.InternalApiKeyFilter;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
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

/**
 * Slice test for {@link InternalTournamentParticipantsController}. Mirrors the
 * {@link InternalTournamentSummaryControllerTest} standaloneSetup + Mockito
 * convention (FASE 6): the {@link InternalApiKeyFilter} is wired into MockMvc
 * via {@code addFilter} so the 401 contract on missing/invalid
 * {@code X-Internal-Api-Key} is verified end-to-end through the filter chain.
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
 * <p>Coverage:
 * <ul>
 *   <li>{@code PUT /internal/tournaments/participants/sync} 200 on a valid body with correct {@code X-Internal-Api-Key};</li>
 *   <li>{@code PUT .../sync} 200 on an empty {@code []} body;</li>
 *   <li>{@code PUT .../sync} 401 on a missing {@code X-Internal-Api-Key} (no delegation to the sync service);</li>
 *   <li>{@code PUT .../sync} 401 on a wrong {@code X-Internal-Api-Key};</li>
 *   <li>Idempotency at the HTTP layer: the same body is accepted twice (the upsert idempotency lives in the sync service).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class InternalTournamentParticipantsControllerTest {

    private static final String INTERNAL_API_KEY = "test-internal-key-123";

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock TournamentParticipantsLocalSyncService tournamentParticipantsLocalSyncService;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new InternalTournamentParticipantsController(tournamentParticipantsLocalSyncService))
                .addFilter(new InternalApiKeyFilter(INTERNAL_API_KEY))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private static TournamentParticipantsEventDto sampleDto(String tournamentId) {
        return new TournamentParticipantsEventDto(
                "evt-" + tournamentId,
                "TOURNAMENT_PARTICIPANTS_UPSERTED",
                tournamentId,
                List.of(new TournamentParticipantViewDto("p-a", false, "Alice",
                        Instant.parse("2026-07-12T10:00:00Z"))),
                null,
                Instant.parse("2026-07-12T10:00:00Z")
        );
    }

    @Test
    void syncTournamentParticipants_200_whenApiKeyValidAndBodyIsValid() throws Exception {
        TournamentParticipantsEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/participants/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isOk());

        verify(tournamentParticipantsLocalSyncService).applyEvents(any());
    }

    @Test
    void syncTournamentParticipants_200_onEmptyBody() throws Exception {
        mvc.perform(put("/internal/tournaments/participants/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());

        verify(tournamentParticipantsLocalSyncService).applyEvents(any());
    }

    @Test
    void syncTournamentParticipants_401_whenApiKeyMissing() throws Exception {
        TournamentParticipantsEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/participants/sync")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isUnauthorized());

        // The filter must short-circuit BEFORE the controller — the service must NOT be invoked.
        verifyNoInteractions(tournamentParticipantsLocalSyncService);
    }

    @Test
    void syncTournamentParticipants_401_whenApiKeyWrong() throws Exception {
        TournamentParticipantsEventDto dto = sampleDto("t-1");

        mvc.perform(put("/internal/tournaments/participants/sync")
                        .header("X-Internal-Api-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(dto))))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(tournamentParticipantsLocalSyncService);
    }

    @Test
    void syncTournamentParticipants_isIdempotentAtHttpLayer_sameBodyAcceptedTwice() throws Exception {
        TournamentParticipantsEventDto dto = sampleDto("t-1");
        String body = objectMapper.writeValueAsString(List.of(dto));

        mvc.perform(put("/internal/tournaments/participants/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mvc.perform(put("/internal/tournaments/participants/sync")
                        .header("X-Internal-Api-Key", INTERNAL_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        // The HTTP layer (controller + filter) is stateless — both calls go through.
        verify(tournamentParticipantsLocalSyncService, times(2)).applyEvents(any());
    }
}