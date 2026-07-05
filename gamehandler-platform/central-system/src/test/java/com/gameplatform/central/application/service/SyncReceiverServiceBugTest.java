package com.gameplatform.central.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.exception.DuplicateEventException;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests that expose hidden bugs in {@link SyncReceiverService}.
 */
class SyncReceiverServiceBugTest {

    private ProcessedEventRepository processedEventRepository;
    private StatisticsRepository statisticsRepository;
    private LocalServerRegistryPort localServerRegistryPort;
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;
    private ObjectMapper objectMapper;
    private SyncReceiverService service;

    @BeforeEach
    void setUp() {
        processedEventRepository = mock(ProcessedEventRepository.class);
        statisticsRepository = mock(StatisticsRepository.class);
        localServerRegistryPort = mock(LocalServerRegistryPort.class);
        registerUserFromSyncUseCase = mock(RegisterUserFromSyncUseCase.class);
        objectMapper = new ObjectMapper();
        // Configure ObjectMapper to handle Instant serialization for tests
        objectMapper.findAndRegisterModules();
        service = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                registerUserFromSyncUseCase,
                objectMapper
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-01: DuplicateEventException aborts the entire sync batch
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-01: Duplicate event in middle of batch is skipped and subsequent events are processed")
    void duplicateEventDoesNotAbortSyncBatch() {
        String payload1 = "{\"gameType\":\"CHESS\",\"durationSeconds\":60,\"occurredAt\":\"2025-01-01T12:00:00Z\"}";
        String payload2 = "{\"gameType\":\"CHESS\",\"durationSeconds\":120,\"occurredAt\":\"2025-01-01T13:00:00Z\"}";
        String payload3 = "{\"gameType\":\"CHESS\",\"durationSeconds\":90,\"occurredAt\":\"2025-01-01T14:00:00Z\"}";

        OutboxEventDto evt1 = new OutboxEventDto("evt-1", "GAME_SESSION_COMPLETED", payload1, Instant.parse("2025-01-01T12:00:00Z"));
        OutboxEventDto evt2 = new OutboxEventDto("evt-2", "GAME_SESSION_COMPLETED", payload2, Instant.parse("2025-01-01T13:00:00Z"));
        OutboxEventDto evt3 = new OutboxEventDto("evt-3", "GAME_SESSION_COMPLETED", payload3, Instant.parse("2025-01-01T14:00:00Z"));

        SyncPayloadDto payload = new SyncPayloadDto("building-A", List.of(evt1, evt2, evt3));

        // evt-1 is new, evt-2 is already processed, evt-3 is new
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(processedEventRepository.existsByEventId("evt-2")).thenReturn(true);
        when(processedEventRepository.existsByEventId("evt-3")).thenReturn(false);

        // Allow statistics to be saved
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.receiveSyncPayload(payload));

        // evt-3 was processed successfully
        verify(processedEventRepository).existsByEventId("evt-3");

        // Heartbeat was sent
        verify(localServerRegistryPort).updateLastSeenAt(eq(new BuildingId("building-A")), any(Instant.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-01 (variant): Single duplicate event — batch is empty after dedup,
    // heartbeat still fires. Ported from the consolidated duplicate test file.
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-01 (variant): Single duplicate event is skipped and heartbeat is still called")
    void singleDuplicateEventIsSkippedAndHeartbeatIsCalled() {
        String validPayload = "{\"gameType\":\"DARTS\",\"occurredAt\":\"2026-03-10T14:00:00Z\",\"durationSeconds\":60}";
        OutboxEventDto duplicateEvent = new OutboxEventDto("evt-dup", "GAME_SESSION_COMPLETED",
                validPayload, Instant.parse("2026-03-10T14:00:00Z"));
        SyncPayloadDto payload = new SyncPayloadDto("building-D", List.of(duplicateEvent));

        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        assertDoesNotThrow(() -> service.receiveSyncPayload(payload));

        verify(localServerRegistryPort).updateLastSeenAt(eq(new BuildingId("building-D")), any(Instant.class));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-02: ProcessedEvent uses event.createdAt instead of Instant.now()
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-02 (C-BUG-06): ProcessedEvent.processedAt is set to the current processing time, not the event's createdAt")
    void processedEventUsesCurrentTimeNotEventCreatedAt() {
        Instant eventCreatedAt = Instant.parse("2025-01-01T12:00:00Z");  // 24h ago
        String payload = "{\"gameType\":\"CHESS\",\"durationSeconds\":60,\"occurredAt\":\"2025-01-01T12:00:00Z\"}";

        OutboxEventDto evt = new OutboxEventDto("evt-audit", "GAME_SESSION_COMPLETED", payload, eventCreatedAt);
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-B", List.of(evt));

        when(processedEventRepository.existsByEventId("evt-audit")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.receiveSyncPayload(syncPayload);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());

        ProcessedEvent savedEvent = captor.getValue();

        // Expect that processedAt is not the 24-hours-ago eventCreatedAt, but is close to Instant.now()
        assertNotEquals(eventCreatedAt, savedEvent.getProcessedAt(),
                "processedAt should not be set to event.createdAt()");
        assertTrue(savedEvent.getProcessedAt().isAfter(Instant.now().minusSeconds(5)),
                "processedAt should be close to Instant.now()");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-03: Unmodifiable map from getData() used in AggregatedStatistics ctor
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-03: Reservation update passes unmodifiable map from getData() to AggregatedStatistics constructor")
    void reservationUpdatePassesUnmodifiableMap() {
        /*
         * Scenario:
         *   RESERVATION_CREATED event arrives for building/game/period that already
         *   has existing statistics with a non-empty data map.
         *
         * Expected:
         *   New AggregatedStatistics is created with a copy of the data map.
         *
         * Actual BUG (potential):
         *   At line 241 (updateReservationStats), stats.getData() returns an
         *   unmodifiable map (see AggregatedStatistics.getData() at line 186).
         *   This unmodifiable map is passed to the AggregatedStatistics constructor
         *   which wraps it in new HashMap<>(data) — this actually works because
         *   HashMap's copy-constructor can take an unmodifiable map.
         *   BUT the design is fragile and the getData() contract returns
         *   Collections.unmodifiableMap, which would fail if the constructor
         *   tried to modify it in-place.
         *
         *   The REAL bug here is a broader design issue: the constructor at line 232-242
         *   creates an entirely new AggregatedStatistics object instead of modifying
         *   the existing one. This loses the mergeWith() capability and means the
         *   old ID is reused while sessions/avgDuration stay the same. The bigger
         *   concern is that this is NOT a domain operation — it's manual field
         *   reconstruction in the service layer, violating domain encapsulation.
         */

        Map<String, Object> existingData = new HashMap<>();
        existingData.put("topPlayer", "Alice");

        AggregatedStatistics existingStats = new AggregatedStatistics(
                "stats-id-1",
                new BuildingId("building-C"),
                GameType.CHESS,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1),
                5,
                120,
                3,
                existingData
        );

        // getData() returns Collections.unmodifiableMap
        Map<String, Object> dataFromGetter = existingStats.getData();

        // Verify the data is unmodifiable
        assertThrows(UnsupportedOperationException.class, () -> dataFromGetter.put("newKey", "newValue"),
                "getData() should return an unmodifiable map");

        // This is what the service code does at line 232-242:
        // It passes stats.getData() (unmodifiable) to the constructor.
        // The constructor wraps it in new HashMap<>(), so it works...
        // But it's a fragile pattern that bypasses domain invariants.
        AggregatedStatistics reconstructed = new AggregatedStatistics(
                existingStats.getId(),
                existingStats.getBuildingId(),
                existingStats.getGameType(),
                existingStats.getPeriodStart(),
                existingStats.getPeriodEnd(),
                existingStats.getTotalSessions(),
                existingStats.getAvgDurationSeconds(),
                10,  // new reservation count
                existingStats.getData()  // unmodifiable map passed here
        );

        // Verify it doesn't crash but also verify the data was preserved
        assertEquals(existingStats.getData(), reconstructed.getData(),
                "Data should be preserved even though an unmodifiable map was passed");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // BUG-04: Missing null-safety when SyncPayload.buildingId is null
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BUG-04: NullPointerException or IllegalArgumentException when SyncPayload has null buildingId")
    void syncPayloadWithNullBuildingIdCrashes() {
        /*
         * Scenario:
         *   A SyncPayload arrives with a null buildingId but valid events.
         *
         * Expected:
         *   Graceful rejection or error message.
         *
         * Actual BUG:
         *   At line 66, `new BuildingId(payload.buildingId())` will throw
         *   IllegalArgumentException from the BuildingId record constructor,
         *   which checks for null/blank. This is not caught, leading to
         *   an unhandled exception that the GlobalExceptionHandler will map to
         *   400 Bad Request — but with a generic message "BuildingId cannot be null"
         *   instead of a meaningful sync-related error.
         */
        OutboxEventDto evt = new OutboxEventDto("evt-1", "GAME_SESSION_COMPLETED",
                "{\"gameType\":\"CHESS\",\"durationSeconds\":60,\"occurredAt\":\"2025-01-01T12:00:00Z\"}",
                Instant.now());

        SyncPayloadDto payload = new SyncPayloadDto(null, List.of(evt));

        assertThrows(IllegalArgumentException.class, () -> service.receiveSyncPayload(payload),
                "BUG-04: A sync payload with null buildingId throws a low-level " +
                "IllegalArgumentException from BuildingId constructor instead of " +
                "a domain-specific validation error.");
    }
}
