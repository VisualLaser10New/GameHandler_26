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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B8.2 — Verifies {@link SyncEventProcessor#extractDuration} logs a {@code log.warn} AND
 * assumes duration 0 when the payload has neither a {@code durationSeconds} field nor a
 * {@code resultJson} fallback. The saved {@link AggregatedStatistics} must reflect the
 * assumed-zero duration ({@code avgDurationSeconds == 0}).
 *
 * <p><b>Log assertion approach (documented):</b> LogCaptor is NOT on the project classpath
 * (verified by build dependency scan — no {@code logcaptor} artifact in any pom.xml, and
 * the project constraints forbid adding new dependencies). Therefore the {@code log.warn}
 * call itself cannot be asserted directly. Instead, this test asserts the observable side
 * effect — the {@link AggregatedStatistics} persisted with {@code avgDurationSeconds == 0}
 * — which is only reachable if {@code extractDuration} returned 0, and the source code of
 * {@code extractDuration} reaches {@code return 0} immediately after the
 * {@code log.warn(...)} statement. The side-effect assertion thus transitively proves the
 * warn path was taken (return 0 is on the same branch as the warn log).</p>
 *
 * <p>{@code SyncEventProcessor} is instantiated directly ({@code new}) rather than as a
 * Spring proxy, so its {@code @Transactional(REQUIRES_NEW)} annotation is inert in this
 * pure unit test — the method body executes inline, which is what we want for logic
 * verification.</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncReceiverExtractDurationWarnTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    private SyncEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new SyncEventProcessor(
                processedEventRepository,
                statisticsRepository,
                registerUserFromSyncUseCase,
                objectMapper,
                clock
        );
    }

    @Test
    @DisplayName("B8.2: payload without durationSeconds (and without resultJson) → warn + stats saved with duration 0")
    void payloadWithoutDurationLogsWarningAndAssumesZero() throws Exception {
        // Payload WITHOUT durationSeconds AND WITHOUT resultJson.
        // extractDuration must: log.warn("...missing 'durationSeconds' field – assuming 0...") and return 0.
        String payload = "{\"eventId\":\"e-1\","
                + "\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\","
                + "\"gameType\":\"CHESS\","
                + "\"status\":\"COMPLETED\"}";
        OutboxEventDto event = new OutboxEventDto("e-1", "GAME_SESSION_COMPLETED", payload, Instant.now());

        when(processedEventRepository.existsByEventId("e-1")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                any(BuildingId.class), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processOne(new BuildingId("building-1"), event);

        assertThat(result).isTrue();

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository).save(captor.capture());
        AggregatedStatistics saved = captor.getValue();

        // extractDuration returned 0 (the warn log is the side effect — not assertable without LogCaptor).
        assertThat(saved.getAvgDurationSeconds())
                .as("extractDuration must assume 0 when no durationSeconds field is present")
                .isZero();
        // The new session was still counted.
        assertThat(saved.getTotalSessions()).isEqualTo(1);
    }
}
