package com.gameplatform.central.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S3 / C-R1 — deterministic unit test of the first-bucket insert-race retry contract
 * exposed by the three {@code update*Stats} methods in {@link SyncEventProcessor}.
 *
 * <p>Each scenario simulates the concurrent first-bucket race by stubbing
 * {@link StatisticsRepository#save} to throw {@link DataIntegrityViolationException}
 * on the first invocation (the loser's {@code saveAndFlush}), then on the retry
 * to return normally. The contract asserted (post-fix):
 * <ol>
 *   <li>{@link StatisticsRepository#findByBuildingAndTypeAndPeriodWithLock} is called
 *       <b>twice</b> — once for the initial bucket-absent check, once again inside
 *       the catch to re-fetch the winner row.</li>
 *   <li>{@link StatisticsRepository#save} is called <b>twice</b> — first raises DIVE,
 *       second persists the merged winner.</li>
 *   <li>The merged {@link AggregatedStatistics} reflects the sum of the winner's
 *       counters and the freshly-arrived delta (e.g. {@code totalSessions==2},
 *       {@code totalAbortedSessions==2}, {@code totalReservations==2}).</li>
 *   <li>{@link SyncEventProcessor#processOne} returns {@code true} (no exception
 *       propagated; the race is contained inside the application service).</li>
 * </ol>
 *
 * <p>This test is the deterministic, mock-driven counterpart to the H2-backed
 * {@code AggregatedStatisticsFirstBucketInsertRaceConcurrencyIT}: where the IT
 * relies on actual concurrent INSERTs hitting the unique constraint, this test
 * stubs the contract explicitly so the merge-retry path is exercised regardless
 * of H2/InnoDB concurrency semantics.</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncEventProcessorFirstBucketRetryMergeUnitTest {

    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private StatisticsRepository statisticsRepository;
    @Mock private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();
    private SyncEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SyncEventProcessor(
                processedEventRepository, statisticsRepository,
                registerUserFromSyncUseCase, objectMapper, clock);
    }

    @Test
    @DisplayName("updateSessionStats: DIVE on first-bucket INSERT retried via merge with the winner row (totalSessions==2)")
    void updateSessionStatsRetriesViaMergeOnDive() throws Exception {
        BuildingId buildingId = new BuildingId("b-1");
        AggregatedStatistics winner = new AggregatedStatistics(
                "winner-id", buildingId, GameType.CHESS,
                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5),
                1, 120, 0, 0, // totalSessions=1 (produced by the other concurrent thread)
                new HashMap<>());

        when(processedEventRepository.existsByEventId("e-1")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty())          // initial: bucket absent
                .thenReturn(Optional.of(winner));       // retry: winner row now visible
        // First save throws DIVE (loser's INSERT hits UK); second save (merge) succeeds.
        when(statisticsRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_building_type_period"))
                .thenAnswer(inv -> inv.getArgument(0));

        String payload = "{\"eventId\":\"e-1\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\",\"gameType\":\"CHESS\",\"durationSeconds\":240,"
                + "\"status\":\"COMPLETED\"}";
        boolean result = processor.processOne(buildingId,
                new OutboxEventDto("e-1", "GAME_SESSION_COMPLETED", payload, Instant.now()));

        assertThat(result).isTrue();
        verify(statisticsRepository, times(2)).findByBuildingAndTypeAndPeriodWithLock(any(), any(), any());

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository, times(2)).save(captor.capture());
        List<AggregatedStatistics> savedCalls = captor.getAllValues();
        // Second save is the merge-retry path (winner + delta).
        AggregatedStatistics merged = savedCalls.get(1);
        assertThat(merged).isSameAs(winner);
        assertThat(merged.getTotalSessions())
                .as("winner(1) + delta(1) == 2 — loser's session counted via merge, no lost update")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("updateAbortedStats: DIVE on first-bucket INSERT retried via merge with the winner row (totalAbortedSessions==2)")
    void updateAbortedStatsRetriesViaMergeOnDive() throws Exception {
        BuildingId buildingId = new BuildingId("b-2");
        AggregatedStatistics winner = new AggregatedStatistics(
                "winner-id", buildingId, GameType.CHESS,
                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5),
                0, 0, 0, 1, // totalAbortedSessions=1 (produced by the other concurrent thread)
                new HashMap<>());

        when(processedEventRepository.existsByEventId("e-2")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(statisticsRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_building_type_period"))
                .thenAnswer(inv -> inv.getArgument(0));

        String payload = "{\"eventId\":\"e-2\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-2\",\"gameType\":\"CHESS\",\"durationSeconds\":0,"
                + "\"status\":\"ABORTED\",\"stopReason\":\"TIMEOUT\"}";
        boolean result = processor.processOne(buildingId,
                new OutboxEventDto("e-2", "GAME_SESSION_ABORTED", payload, Instant.now()));

        assertThat(result).isTrue();
        verify(statisticsRepository, times(2)).findByBuildingAndTypeAndPeriodWithLock(any(), any(), any());

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository, times(2)).save(captor.capture());
        AggregatedStatistics merged = captor.getAllValues().get(1);
        assertThat(merged).isSameAs(winner);
        assertThat(merged.getTotalAbortedSessions())
                .as("winner(1) + delta(1) == 2 — both aborted sessions counted via merge")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("updateReservationStats: DIVE on first-bucket INSERT retried via merge with the winner row (totalReservations==2)")
    void updateReservationStatsRetriesViaMergeOnDive() throws Exception {
        BuildingId buildingId = new BuildingId("b-3");
        AggregatedStatistics winner = new AggregatedStatistics(
                "winner-id", buildingId, GameType.CHESS,
                LocalDate.of(2026, 7, 5), LocalDate.of(2026, 7, 5),
                0, 0, 1, 0, // totalReservations=1 (produced by the other concurrent thread)
                new HashMap<>());

        when(processedEventRepository.existsByEventId("e-3")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(statisticsRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("uk_building_type_period"))
                .thenAnswer(inv -> inv.getArgument(0));

        String payload = "{\"eventId\":\"e-3\",\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-3\",\"gameType\":\"CHESS\",\"durationSeconds\":0,"
                + "\"status\":\"COMPLETED\"}";
        boolean result = processor.processOne(buildingId,
                new OutboxEventDto("e-3", "RESERVATION_CREATED", payload, Instant.now()));

        assertThat(result).isTrue();
        verify(statisticsRepository, times(2)).findByBuildingAndTypeAndPeriodWithLock(any(), any(), any());

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository, times(2)).save(captor.capture());
        AggregatedStatistics merged = captor.getAllValues().get(1);
        assertThat(merged).isSameAs(winner);
        assertThat(merged.getTotalReservations())
                .as("winner(1) + delta(1) == 2 — both reservations counted via merge")
                .isEqualTo(2);
    }
}
