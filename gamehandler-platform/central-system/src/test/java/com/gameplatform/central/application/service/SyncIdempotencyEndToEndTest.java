package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FASE 4 — idempotency end-to-end proof: the same sync event received twice
 * results in exactly one statistics write.
 *
 * <p>Mirrors {@link SyncReceiverServiceTest} mock setup (pure unit, no Spring
 * context). The {@link ProcessedEventRepository#existsByEventId} stub returns
 * {@code false} on the first invocation and {@code true} on the second,
 * emulating the DB recording the event after the first sync completes.</p>
 *
 * <p>Simulates local retry 3 volte → central conta 1 (idempotency via
 * processed_events PK + existsByEventId check).</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncIdempotencyEndToEndTest {

    private static final String EVENT_ID = "evt-Y";
    private static final String BUILDING_ID = "building-2";
    private static final String PAYLOAD =
            "{\"gameType\":\"CHESS\",\"occurredAt\":\"2026-07-05T12:00:00Z\",\"durationSeconds\":120}";

    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private StatisticsRepository statisticsRepository;
    @Mock private LocalServerRegistryPort localServerRegistryPort;
    @Mock private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void receiveSameEventTwice_recordsStatsExactlyOnce() {
        OutboxEventDto event = new OutboxEventDto(
                EVENT_ID, "GAME_SESSION_COMPLETED", PAYLOAD, Instant.now());
        SyncPayloadDto payload = new SyncPayloadDto(BUILDING_ID, List.of(event));

        // DB records the event after the first sync: false on first call, true thereafter.
        when(processedEventRepository.existsByEventId(EVENT_ID)).thenReturn(false, true);
        // Empty existing stats → first sync inserts a new AggregatedStatistics row.
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncReceiverService service = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                registerUserFromSyncUseCase,
                objectMapper);

        // Sync 1 — first reception: stats written, event marked processed.
        service.receiveSyncPayload(payload);
        // Sync 2 — local retry of the SAME event: dedup'd by existsByEventId check.
        service.receiveSyncPayload(payload);

        // Exactly-once: statisticsRepository.save called only on the first sync.
        verify(statisticsRepository, times(1)).save(any());
        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
        verify(processedEventRepository, times(2)).existsByEventId(EVENT_ID);
        // Heartbeat fires once per receiveSyncPayload regardless of dedup.
        verify(localServerRegistryPort, times(2)).updateLastSeenAt(any(), any());
    }
}
