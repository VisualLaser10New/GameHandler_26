package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.model.ProcessedEvent;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B4.6 — Dedicated unit test for the GAME_SESSION_ABORTED branch of
 * {@link SyncEventProcessor}. Verifies that an aborted-session event
 * increments {@code totalAbortedSessions} and leaves {@code totalSessions}
 * untouched (insert path: no pre-existing stats row).
 */
@ExtendWith(MockitoExtension.class)
class GameSessionAbortedSyncTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    @Test
    void abortedEventIncrementsAbortedSessionsNotTotalSessions() throws Exception {
        // Given: a GAME_SESSION_ABORTED outbox event
        String payload = "{\"eventId\":\"e-1\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\",\"gameType\":\"CHESS\","
                + "\"durationSeconds\":0,\"status\":\"ABORTED\",\"stopReason\":\"TIMEOUT\"}";
        OutboxEventDto event = new OutboxEventDto("e-1", "GAME_SESSION_ABORTED", payload, Instant.now());
        BuildingId buildingId = new BuildingId("building-1");

        // Stub: not yet processed -> process the event
        when(processedEventRepository.existsByEventId("e-1")).thenReturn(false);
        // Stub: no existing stats row -> insert path
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                eq(buildingId), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        // Stub: save returns its argument
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // When: process the event directly via SyncEventProcessor
        SyncEventProcessor processor = new SyncEventProcessor(
                processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock);
        boolean result = processor.processOne(buildingId, event);

        // Then: stats saved with totalAbortedSessions=1, totalSessions=0
        assertThat(result).isTrue();
        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository).save(captor.capture());
        AggregatedStatistics saved = captor.getValue();
        assertThat(saved.getTotalAbortedSessions()).isEqualTo(1);
        assertThat(saved.getTotalSessions()).isZero();
        // Aborted sessions must not trigger user registration
        verify(registerUserFromSyncUseCase, never()).registerFromSync(any());
        // Event must be marked processed
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }
}
