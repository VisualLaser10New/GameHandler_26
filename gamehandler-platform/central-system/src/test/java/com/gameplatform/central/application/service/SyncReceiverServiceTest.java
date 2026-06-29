package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SyncReceiverService}, covering:
 * <ul>
 *   <li>Malformed / missing gameType – no exception thrown, ProcessedEvent recorded</li>
 *   <li>Heartbeat update after successful sync</li>
 *   <li>Locked stats retrieval for concurrent-safety</li>
 *   <li>Happy-path GAME_SESSION_COMPLETED and RESERVATION_CREATED processing</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SyncReceiverServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    private SyncReceiverService syncReceiverService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        syncReceiverService = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                objectMapper
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Malformed gameType – resilience
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void receiveSyncPayload_shouldNotThrow_whenGameTypeIsMissing() {
        String payload = "{\"occurredAt\":\"" + Instant.now() + "\",\"durationSeconds\":120}";
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);

        // Must NOT throw even though gameType is missing
        assertThatCode(() -> syncReceiverService.receiveSyncPayload(syncPayload))
                .doesNotThrowAnyException();

        // ProcessedEvent must still be recorded to prevent re-processing
        verify(processedEventRepository, atLeastOnce()).save(any(ProcessedEvent.class));
    }

    @Test
    void receiveSyncPayload_shouldNotThrow_whenGameTypeIsInvalid() {
        String payload = "{\"gameType\":\"INVALID_GAME\",\"occurredAt\":\"" + Instant.now() + "\",\"durationSeconds\":60}";
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);

        assertThatCode(() -> syncReceiverService.receiveSyncPayload(syncPayload))
                .doesNotThrowAnyException();

        // No stats update should have happened
        verify(statisticsRepository, never()).findByBuildingAndTypeAndPeriodWithLock(any(), any(), any());
    }

    @Test
    void receiveSyncPayload_shouldRecordProcessedEvent_evenForMalformedGameType() {
        String payload = "{\"occurredAt\":\"" + Instant.now() + "\"}";  // no gameType
        OutboxEventDto event = new OutboxEventDto("evt-malformed",
                "RESERVATION_CREATED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-2", List.of(event));

        when(processedEventRepository.existsByEventId("evt-malformed")).thenReturn(false);

        syncReceiverService.receiveSyncPayload(syncPayload);

        // Must record as processed to avoid infinite reprocessing
        verify(processedEventRepository, atLeastOnce()).save(
                argThat(pe -> "evt-malformed".equals(pe.getEventId()))
        );
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Heartbeat update
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void receiveSyncPayload_shouldCallUpdateLastSeenAt_afterSuccessfulProcessing() {
        String payload = "{\"gameType\":\"CHESS\",\"occurredAt\":\"" + Instant.now() + "\",\"durationSeconds\":300}";
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-42", List.of(event));

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                any(BuildingId.class), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenReturn(buildEmptyStats());

        syncReceiverService.receiveSyncPayload(syncPayload);

        // Heartbeat: updateLastSeenAt must be called with the correct buildingId
        ArgumentCaptor<BuildingId> bidCaptor = ArgumentCaptor.forClass(BuildingId.class);
        verify(localServerRegistryPort).updateLastSeenAt(bidCaptor.capture(), any(Instant.class));
        assertThat(bidCaptor.getValue().id()).isEqualTo("building-42");
    }

    @Test
    void receiveSyncPayload_shouldNotCallUpdateLastSeenAt_whenPayloadIsNullOrEmpty() {
        syncReceiverService.receiveSyncPayload(null);
        syncReceiverService.receiveSyncPayload(new SyncPayloadDto("building-1", List.of()));

        verify(localServerRegistryPort, never()).updateLastSeenAt(any(), any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Locked stats retrieval
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void receiveSyncPayload_shouldUsePessimisticLockQuery_forSessionStats() {
        String payload = "{\"gameType\":\"CHESS\",\"occurredAt\":\"" + Instant.now() + "\",\"durationSeconds\":200}";
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenReturn(buildEmptyStats());

        syncReceiverService.receiveSyncPayload(syncPayload);

        // Verify the LOCKED variant was used, not the plain one
        verify(statisticsRepository).findByBuildingAndTypeAndPeriodWithLock(any(), any(), any());
        verify(statisticsRepository, never()).findByBuildingAndTypeAndPeriod(any(), any(), any());
    }

    @Test
    void receiveSyncPayload_shouldMergeStats_whenExistingStatsFoundWithLock() {
        BuildingId buildingId = new BuildingId("building-1");
        AggregatedStatistics existing = new AggregatedStatistics(
                UUID.randomUUID().toString(),
                buildingId,
                GameType.CHESS,
                LocalDate.now(),
                LocalDate.now(),
                5, 120, 0,
                new java.util.HashMap<>()
        );

        String payload = "{\"gameType\":\"CHESS\",\"occurredAt\":\"" + Instant.now() + "\",\"durationSeconds\":180}";
        OutboxEventDto event = new OutboxEventDto(UUID.randomUUID().toString(),
                "GAME_SESSION_COMPLETED", payload, Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId(event.eventId())).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                any(BuildingId.class), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        syncReceiverService.receiveSyncPayload(syncPayload);

        // The merged stats must be saved with 6 total sessions (5 existing + 1 new)
        ArgumentCaptor<AggregatedStatistics> statsCaptor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository).save(statsCaptor.capture());
        assertThat(statsCaptor.getValue().getTotalSessions()).isEqualTo(6);
    }

    @Test
    void receiveSyncPayload_shouldSkipAlreadyProcessedEvents() {
        OutboxEventDto event = new OutboxEventDto("already-done",
                "GAME_SESSION_COMPLETED", "{}", Instant.now());
        SyncPayloadDto syncPayload = new SyncPayloadDto("building-1", List.of(event));

        when(processedEventRepository.existsByEventId("already-done")).thenReturn(true);

        // Must not throw DuplicateEventException and complete successfully (skipping duplicate)
        assertThatCode(() -> syncReceiverService.receiveSyncPayload(syncPayload))
                .doesNotThrowAnyException();

        verify(statisticsRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────────────────────────────────

    private AggregatedStatistics buildEmptyStats() {
        return new AggregatedStatistics(
                UUID.randomUUID().toString(),
                new BuildingId("building-1"),
                GameType.CHESS,
                LocalDate.now(),
                LocalDate.now(),
                0, 0, 0,
                new java.util.HashMap<>()
        );
    }
}
