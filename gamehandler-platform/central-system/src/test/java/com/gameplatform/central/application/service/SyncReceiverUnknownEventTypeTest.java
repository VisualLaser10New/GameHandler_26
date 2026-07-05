package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.OutboxEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B9.2 — Dedicated unit test for the unknown-event-type branch of
 * {@link SyncEventProcessor}. Verifies that an unrecognised eventType logs a
 * warning and is marked processed WITHOUT touching statistics.
 *
 * <p>LogCaptor is not on the classpath; behaviour is verified via side effects
 * (no stats update + processed mark).</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncReceiverUnknownEventTypeTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    @Test
    void unknownEventTypeLogsWarningAndMarksProcessed() throws Exception {
        // LogCaptor not on classpath; behaviour verified via side effects
        // (no stats update + processed mark).
        String payload = "{\"eventId\":\"e-1\",\"occurredAt\":\"2026-07-05T12:00:00Z\"}";
        OutboxEventDto event = new OutboxEventDto("e-1", "TOTALLY_UNKNOWN_TYPE", payload, Instant.now());

        when(processedEventRepository.existsByEventId("e-1")).thenReturn(false);

        SyncEventProcessor processor = new SyncEventProcessor(
                processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock);
        boolean result = processor.processOne(new BuildingId("building-1"), event);

        // Unknown eventType returns true (marks processed) but performs no stats update.
        assertThat(result).isTrue();
        verify(statisticsRepository, never()).save(any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
    }
}
