package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.in.CancelTournamentUseCase;
import com.gameplatform.central.domain.ports.in.CreateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.DeleteTournamentUseCase;
import com.gameplatform.central.domain.ports.in.EmitTournamentSummaryUseCase;
import com.gameplatform.central.domain.ports.in.OpenTournamentRegistrationUseCase;
import com.gameplatform.central.domain.ports.in.RegisterTournamentParticipantUseCase;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.in.ScheduleTournamentMatchesUseCase;
import com.gameplatform.central.domain.ports.in.UpdateTournamentUseCase;
import com.gameplatform.central.domain.ports.in.UpsertGameDefinitionUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.GameDefinitionUpsertRequestedEventDto;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.ParticipantRegisterRequestedEventDto;
import com.gameplatform.shared.dto.TournamentCreateRequestedEventDto;
import com.gameplatform.shared.dto.TournamentDeleteRequestedEventDto;
import com.gameplatform.shared.dto.TournamentLifecycleRequestedEventDto;
import com.gameplatform.shared.dto.TournamentUpdateRequestedEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FASE 7.A dedicated unit tests for the nine {@code *_REQUESTED} branches of
 * {@link SyncEventProcessor} ({@code USER_REGISTERED} is out of scope). The
 * processor is built with the 21-arg production ctor so every FASE 7-A3/S3
 * admin-request use case is a non-null mock (the legacy 5/7/11-arg ctors pass
 * {@code null} for those and short-circuit the branch — covered separately by
 * the legacy test suites).
 *
 * <p>Pattern:
 * <ul>
 *   <li>{@code existsByEventId → false} so processing proceeds;</li>
 *   <li>real {@link ObjectMapper} + {@link JavaTimeModule} serialises a real
 *       event DTO record to JSON as the outbox payload (no invented fields);</li>
 *   <li>{@code processor.processOne(buildingId, event)} runs the matching
 *       branch;</li>
 *   <li>verify the delegated use case is invoked exactly once with the right
 *       {@code originatingRequestId == dto.requestId()};</li>
 *   <li>verify {@code processedEventRepository.save(ProcessedEvent)} is called
 *       once (idempotency-mark) and the processor returns {@code true}.</li>
 * </ul>
 *
 * <p>The SCHEDULE branch is intentionally asserted only on
 * {@code scheduleTournamentMatchesUseCase.schedule(...)} + processed-mark — the
 * BUG-SCHEDULE-REQUEST-ID return-emit of {@code TOURNAMENT_SUMMARY_UPSERTED} is
 * verified by the dedicated BUG-SCHEDULE-REQUEST-ID integration test, not here,
 * per SA7 scope.</p>
 *
 * <p>The malformed-JSON addendum test verifies the BUG-JSON-PARSE fix (SA6): a
 * payload of {@code "{}invalid"} on a {@code *_REQUESTED} event-type is caught
 * as {@link com.fasterxml.jackson.core.JsonProcessingException}, marked
 * processed, and the processor returns {@code false} rather than throwing
 * (poison-isolation).</p>
 *
 * <p>{@code ROLE_ASSIGNMENT_REQUESTED} (the 9th {@code *_REQUESTED} branch) is
 * structurally identical to {@code GAME_DEFINITION_UPSERT_REQUESTED} (thin
 * delegation to {@link com.gameplatform.central.domain.ports.in.UpdateUserUseCase})
 * and is covered by the existing {@code UserServiceFromSync*} test suites; this
 * class focuses on the 8 tournament/game-definition branches the FASE 7.A plan
 * pins.</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncEventProcessorRequestedBranchTest {

    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private StatisticsRepository statisticsRepository;
    @Mock private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;
    @Mock private StatisticsFirstBucketRaceRetryHelper retryHelper;
    @Mock private PlayerStatisticsProjectionService playerStatisticsProjection;
    @Mock private TournamentBracketService tournamentBracketService;
    @Mock private TournamentStandingsService tournamentStandingsService;
    @Mock private TournamentRepository tournamentRepository;
    @Mock private TournamentMatchRepository tournamentMatchRepository;

    @Mock private com.gameplatform.central.domain.ports.in.UpdateUserUseCase updateUserUseCase;
    @Mock private UpsertGameDefinitionUseCase upsertGameDefinitionUseCase;
    @Mock private CreateTournamentUseCase createTournamentUseCase;
    @Mock private OpenTournamentRegistrationUseCase openTournamentRegistrationUseCase;
    @Mock private CancelTournamentUseCase cancelTournamentUseCase;
    @Mock private ScheduleTournamentMatchesUseCase scheduleTournamentMatchesUseCase;
    @Mock private UpdateTournamentUseCase updateTournamentUseCase;
    @Mock private DeleteTournamentUseCase deleteTournamentUseCase;
    @Mock private RegisterTournamentParticipantUseCase registerTournamentParticipantUseCase;
    @Mock private EmitTournamentSummaryUseCase emitTournamentSummaryUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final Clock clock = Clock.systemUTC();
    private SyncEventProcessor processor;
    private final BuildingId buildingId = new BuildingId("building-1");

    @BeforeEach
    void setUp() {
        processor = new SyncEventProcessor(
                processedEventRepository,
                statisticsRepository,
                registerUserFromSyncUseCase,
                objectMapper,
                clock,
                retryHelper,
                playerStatisticsProjection,
                tournamentBracketService,
                tournamentStandingsService,
                tournamentRepository,
                tournamentMatchRepository,
                updateUserUseCase,
                upsertGameDefinitionUseCase,
                createTournamentUseCase,
                openTournamentRegistrationUseCase,
                cancelTournamentUseCase,
                scheduleTournamentMatchesUseCase,
                updateTournamentUseCase,
                deleteTournamentUseCase,
                registerTournamentParticipantUseCase,
                emitTournamentSummaryUseCase
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_CREATE_REQUESTED → CreateTournamentUseCase.create
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentCreateRequested_callsCreateUseCaseAndMarksProcessed() throws Exception {
        TournamentCreateRequestedEventDto dto = new TournamentCreateRequestedEventDto(
                "e-create", "TOURNAMENT_CREATE_REQUESTED", "e-create",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "Test Cup", GameType.CHESS, false, 1,
                Instant.parse("2026-08-01T10:00:00Z"),
                List.of("b-1", "b-2"),
                Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_CREATE_REQUESTED", "e-create");

        when(processedEventRepository.existsByEventId("e-create")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(createTournamentUseCase).create(any(Tournament.class), eq(dto.buildingIds()), eq(dto.requestId()));
        verifyMarkedProcessed("e-create");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_OPEN_REQUESTED → OpenTournamentRegistrationUseCase.open
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentOpenRequested_callsOpenUseCaseAndMarksProcessed() throws Exception {
        TournamentLifecycleRequestedEventDto dto = new TournamentLifecycleRequestedEventDto(
                "e-open", "TOURNAMENT_OPEN_REQUESTED", "e-open",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "t-1", Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_OPEN_REQUESTED", "e-open");

        when(processedEventRepository.existsByEventId("e-open")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(openTournamentRegistrationUseCase).open(eq(new TournamentId("t-1")), eq(dto.requestId()));
        verifyMarkedProcessed("e-open");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_CANCEL_REQUESTED → CancelTournamentUseCase.cancel
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentCancelRequested_callsCancelUseCaseAndMarksProcessed() throws Exception {
        TournamentLifecycleRequestedEventDto dto = new TournamentLifecycleRequestedEventDto(
                "e-cancel", "TOURNAMENT_CANCEL_REQUESTED", "e-cancel",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "t-1", Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_CANCEL_REQUESTED", "e-cancel");

        when(processedEventRepository.existsByEventId("e-cancel")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(cancelTournamentUseCase).cancel(eq(new TournamentId("t-1")), eq(dto.requestId()));
        verifyMarkedProcessed("e-cancel");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_SCHEDULE_REQUESTED → ScheduleTournamentMatchesUseCase.schedule
    // (BUG-SCHEDULE-REQUEST-ID: SUMMARY_UPSERTED return-emit assertion is intentionally
    //  omitted — verified by the dedicated BUG-SCHEDULE-REQUEST-ID integration test.)
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentScheduleRequested_callsScheduleUseCaseAndMarksProcessed() throws Exception {
        TournamentLifecycleRequestedEventDto dto = new TournamentLifecycleRequestedEventDto(
                "e-schedule", "TOURNAMENT_SCHEDULE_REQUESTED", "e-schedule",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "t-1", Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_SCHEDULE_REQUESTED", "e-schedule");

        when(processedEventRepository.existsByEventId("e-schedule")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(scheduleTournamentMatchesUseCase).schedule(eq(new TournamentId("t-1")));
        verifyMarkedProcessed("e-schedule");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_UPDATE_REQUESTED → UpdateTournamentUseCase.update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentUpdateRequested_callsUpdateUseCaseAndMarksProcessed() throws Exception {
        TournamentUpdateRequestedEventDto dto = new TournamentUpdateRequestedEventDto(
                "e-update", "TOURNAMENT_UPDATE_REQUESTED", "e-update",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "t-1", "Renamed Cup",
                Instant.parse("2026-09-01T10:00:00Z"),
                List.of("b-1", "b-2", "b-3"),
                Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_UPDATE_REQUESTED", "e-update");

        when(processedEventRepository.existsByEventId("e-update")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(updateTournamentUseCase).update(
                eq(new TournamentId("t-1")),
                eq(dto.name()),
                eq(dto.startsAt()),
                eq(dto.buildingIds()),
                eq(dto.requestId()));
        verifyMarkedProcessed("e-update");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — TOURNAMENT_DELETE_REQUESTED → DeleteTournamentUseCase.delete
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_tournamentDeleteRequested_callsDeleteUseCaseAndMarksProcessed() throws Exception {
        TournamentDeleteRequestedEventDto dto = new TournamentDeleteRequestedEventDto(
                "e-delete", "TOURNAMENT_DELETE_REQUESTED", "e-delete",
                "admin-1", "PLATFORM_ADMIN", "building-1",
                "t-1", Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "TOURNAMENT_DELETE_REQUESTED", "e-delete");

        when(processedEventRepository.existsByEventId("e-delete")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(deleteTournamentUseCase).delete(eq(new TournamentId("t-1")), eq(dto.requestId()));
        verifyMarkedProcessed("e-delete");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — PARTICIPANT_REGISTER_REQUESTED → RegisterTournamentParticipantUseCase.register
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_participantRegisterRequested_callsRegisterUseCaseAndMarksProcessed() throws Exception {
        ParticipantRegisterRequestedEventDto dto = new ParticipantRegisterRequestedEventDto(
                "e-part", "PARTICIPANT_REGISTER_REQUESTED", "e-part",
                "player-1", "PLAYER", "building-1",
                "t-1", "Team Alpha", List.of("player-1", "player-2", "player-3"),
                Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "PARTICIPANT_REGISTER_REQUESTED", "e-part");

        when(processedEventRepository.existsByEventId("e-part")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(registerTournamentParticipantUseCase).register(
                eq(new TournamentId("t-1")),
                eq(new UserId("player-1")),
                eq(dto.teamName()),
                eq(dto.teamMemberIds()),
                eq(dto.requestId()));
        verifyMarkedProcessed("e-part");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // 7.A.7 — GAME_DEFINITION_UPSERT_REQUESTED → UpsertGameDefinitionUseCase.upsert
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_gameDefinitionUpsertRequested_callsUpsertUseCaseAndMarksProcessed() throws Exception {
        GameDefinitionUpsertRequestedEventDto dto = new GameDefinitionUpsertRequestedEventDto(
                "e-game", "GAME_DEFINITION_UPSERT_REQUESTED", "e-game",
                "admin-1", "GAME_ADMIN", "building-1",
                GameType.DARTS, "Darts", 2, 4, true,
                Map.of("mode", "classic"),
                Instant.parse("2026-07-12T10:00:00Z"));
        OutboxEventDto event = wrap(dto, "GAME_DEFINITION_UPSERT_REQUESTED", "e-game");

        when(processedEventRepository.existsByEventId("e-game")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isTrue();
        verify(upsertGameDefinitionUseCase).upsert(any(GameDefinition.class), eq(dto.requestId()));
        verifyMarkedProcessed("e-game");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-JSON-PARSE (SA6) — malformed JSON payload on a *_REQUESTED event-type
    // → JsonProcessingException caught, markProcessed(eventId) fires, returns false.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void processOne_malformedJsonOnRequestedEvent_marksProcessedAndReturnsFalse() throws Exception {
        // BUG-JSON-PARSE (SA6): a payload that cannot be tokenised throws
        // JsonProcessingException from objectMapper.readTree / readValue and is
        // caught by SyncEventProcessor.processOne, which marks the event
        // processed (poison isolation) and returns false rather than re-throwing.
        // Note: "{}invalid" alone does NOT throw under Jackson's default
        // FAIL_ON_TRAILING_TOKENS=disabled — Jackson silently deserialises it to
        // a null-fields record; "{not valid json}" breaks JSON tokenisation at
        // the structural level (missing ':' after the object key) so readTree
        // raises JsonParseException, a JsonProcessingException subclass, which is
        // the exact branch the SA6 fix guards.
        OutboxEventDto event = new OutboxEventDto(
                "e-broken", "TOURNAMENT_CREATE_REQUESTED", "{not valid json}", Instant.now());

        when(processedEventRepository.existsByEventId("e-broken")).thenReturn(false);

        boolean result = processor.processOne(buildingId, event);

        assertThat(result).isFalse();
        // markProcessed(eventId) internally calls processedEventRepository.save(ProcessedEvent)
        verifyMarkedProcessed("e-broken");
        // Use case was NEVER invoked because the JSON parse failed before delegation
        verify(createTournamentUseCase, never()).create(any(), any(), any());
        verify(emitTournamentSummaryUseCase, never()).emitSummary(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private OutboxEventDto wrap(Object dto, String eventType, String eventId) {
        try {
            String payload = objectMapper.writeValueAsString(dto);
            return new OutboxEventDto(eventId, eventType, payload, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void verifyMarkedProcessed(String expectedEventId) {
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(expectedEventId);
    }
}
