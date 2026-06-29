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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncReceiverBatchPoisoningBugTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private LocalServerRegistryPort localServerRegistryPort;

    private SyncReceiverService service;

    @BeforeEach
    void setUp() {
        service = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("BUG-SYNC-01: one event with invalid occurredAt must not poison the whole sync batch")
    void invalidOccurredAtInOneEvent_doesNotAbortSubsequentValidEvents() {
        OutboxEventDto malformedTimestamp = new OutboxEventDto(
                "evt-bad-time",
                "GAME_SESSION_COMPLETED",
                "{\"gameType\":\"CHESS\",\"occurredAt\":\"not-an-instant\",\"durationSeconds\":30}",
                Instant.parse("2026-01-01T10:00:00Z")
        );
        OutboxEventDto valid = new OutboxEventDto(
                "evt-valid-after-bad-time",
                "GAME_SESSION_COMPLETED",
                "{\"gameType\":\"CHESS\",\"occurredAt\":\"2026-01-01T10:05:00Z\",\"durationSeconds\":90}",
                Instant.parse("2026-01-01T10:05:00Z")
        );
        SyncPayloadDto payload = new SyncPayloadDto("building-sync", List.of(malformedTimestamp, valid));

        when(processedEventRepository.existsByEventId("evt-bad-time")).thenReturn(false);
        lenient().when(processedEventRepository.existsByEventId("evt-valid-after-bad-time")).thenReturn(false);
        lenient().when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(statisticsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThatCode(() -> service.receiveSyncPayload(payload))
                .as("A malformed event should be isolated so later events and heartbeat still run")
                .doesNotThrowAnyException();

        ArgumentCaptor<AggregatedStatistics> statsCaptor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository, atLeastOnce()).save(statsCaptor.capture());
        assertThat(statsCaptor.getAllValues())
                .anySatisfy(stats -> {
                    assertThat(stats.getBuildingId()).isEqualTo(new BuildingId("building-sync"));
                    assertThat(stats.getGameType()).isEqualTo(GameType.CHESS);
                    assertThat(stats.getPeriodStart()).isEqualTo(LocalDate.of(2026, 1, 1));
                    assertThat(stats.getTotalSessions()).isEqualTo(1);
                    assertThat(stats.getAvgDurationSeconds()).isEqualTo(90);
                });

        verify(processedEventRepository, atLeastOnce()).save(any(ProcessedEvent.class));
        verify(localServerRegistryPort).updateLastSeenAt(any(BuildingId.class), any(Instant.class));
    }
}
