package com.gameplatform.central;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.application.service.SyncReceiverService;
import com.gameplatform.central.domain.exception.DuplicateEventException;
import com.gameplatform.central.domain.ports.out.LocalServerRegistryPort;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug C-01: DuplicateEventException is thrown inside the for-loop at line 71 of
 * SyncReceiverService, but the catch block at line 79 only catches JsonProcessingException.
 * The uncaught DuplicateEventException propagates out, aborting ALL remaining events
 * in the batch and rolling back the @Transactional method.
 */
@ExtendWith(MockitoExtension.class)
class SyncReceiverServiceBugTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    private ObjectMapper objectMapper;

    private SyncReceiverService syncReceiverService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        syncReceiverService = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                objectMapper
        );
    }

    @Test
    @DisplayName("C-01: Duplicate event #2 is skipped, but event #3 processing continues and heartbeat is updated")
    void duplicateEventInMiddleOfBatch_doesNotAbortRemainingEvents() {
        // Arrange: 3 valid GAME_SESSION_COMPLETED events with valid JSON payloads
        String validPayload = "{\"gameType\":\"CHESS\",\"occurredAt\":\"2026-01-15T10:00:00Z\",\"durationSeconds\":120}";

        OutboxEventDto event1 = new OutboxEventDto("evt-1", "GAME_SESSION_COMPLETED", validPayload, Instant.now());
        OutboxEventDto event2 = new OutboxEventDto("evt-2", "GAME_SESSION_COMPLETED", validPayload, Instant.now());
        OutboxEventDto event3 = new OutboxEventDto("evt-3", "GAME_SESSION_COMPLETED", validPayload, Instant.now());

        SyncPayloadDto payload = new SyncPayloadDto("building-1", List.of(event1, event2, event3));

        // Event #1 is new, event #2 is a duplicate, event #3 is new
        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(processedEventRepository.existsByEventId("evt-2")).thenReturn(true);  // duplicate!
        when(processedEventRepository.existsByEventId("evt-3")).thenReturn(false);

        // Allow statisticsRepository calls for event #1 and event #3
        lenient().when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(java.util.Optional.empty());

        // Act & Assert: Call receiveSyncPayload, should not throw
        assertDoesNotThrow(() -> syncReceiverService.receiveSyncPayload(payload));

        // Verify event #3 was indeed processed
        verify(processedEventRepository).existsByEventId("evt-3");

        // Verify the heartbeat (updateLastSeenAt) was called because processing succeeded
        verify(localServerRegistryPort).updateLastSeenAt(eq(new com.gameplatform.shared.domain.model.BuildingId("building-1")), any(Instant.class));
    }

    @Test
    @DisplayName("C-01: Single duplicate event is skipped and heartbeat is successfully sent")
    void singleDuplicateEvent_isSkippedAndHeartbeatIsCalled() {
        // Arrange: a batch with just 1 event that is a duplicate
        String validPayload = "{\"gameType\":\"DARTS\",\"occurredAt\":\"2026-03-10T14:00:00Z\",\"durationSeconds\":60}";
        OutboxEventDto duplicateEvent = new OutboxEventDto("evt-dup", "GAME_SESSION_COMPLETED", validPayload, Instant.now());
        SyncPayloadDto payload = new SyncPayloadDto("building-2", List.of(duplicateEvent));

        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        // Act & Assert: duplicate is skipped, heartbeat is called
        assertDoesNotThrow(() -> syncReceiverService.receiveSyncPayload(payload));

        verify(localServerRegistryPort).updateLastSeenAt(eq(new com.gameplatform.shared.domain.model.BuildingId("building-2")), any(Instant.class));
    }
}
