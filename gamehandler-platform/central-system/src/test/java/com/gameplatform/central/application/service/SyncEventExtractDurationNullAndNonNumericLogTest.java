package com.gameplatform.central.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.AggregatedStatistics;
import com.gameplatform.central.domain.ports.in.RegisterUserFromSyncUseCase;
import com.gameplatform.central.domain.ports.out.ProcessedEventRepository;
import com.gameplatform.central.domain.ports.out.StatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.dto.OutboxEventDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

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
 * S2 — extends {@link SyncReceiverExtractDurationWarnTest} (which covers only the
 * no-{@code durationSeconds}-key path) with two additional failure modes for
 * {@link SyncEventProcessor#extractDuration}:
 * <ul>
 *   <li>{@code {"durationSeconds": null}} — present-but-null node.</li>
 *   <li>{@code {"durationSeconds": "abc"}} — present-but-non-numeric node.</li>
 * </ul>
 *
 * <p>Both must:
 * <ol>
 *   <li>log a {@code WARN} identifying the precise failure ({@code "null"} /
 *       {@code "non-numeric: <value>"});</li>
 *   <li>fall back to duration {@code 0} via {@code Optional.orElse(0)};</li>
 *   <li>STILL increment {@code totalSessions} by 1 (the count invariant — losing
 *       the count is worse than losing the duration contribution).</li>
 * </ol>
 *
 * <p><b>Log assertion approach:</b> LogCaptor is forbidden by the project
 * constraints (no {@code logcaptor} artifact in any pom.xml). Instead, a Logback
 * {@link ListAppender} is attached directly to the {@link SyncEventProcessor}
 * logger and the captured {@link ILoggingEvent}s are filtered for the expected
 * WARN. This is the project-sanctioned alternative (per the analysis doc).</p>
 *
 * <p>{@code SyncEventProcessor} is instantiated directly ({@code new}) so its
 * {@code @Transactional(REQUIRES_NEW)} annotation is inert in this pure unit
 * test — the method body executes inline.</p>
 */
@ExtendWith(MockitoExtension.class)
class SyncEventExtractDurationNullAndNonNumericLogTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    @Mock
    private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.systemUTC();

    private SyncEventProcessor processor;
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        processor = new SyncEventProcessor(
                processedEventRepository,
                statisticsRepository,
                registerUserFromSyncUseCase,
                objectMapper,
                clock
        );
        logger = (Logger) LoggerFactory.getLogger(SyncEventProcessor.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("S2: {\"durationSeconds\": null} logs WARN ('null') and falls back to 0; totalSessions still incremented")
    void nullDurationLogsWarnAndFallsBackToZero() throws Exception {
        String payload = "{\"eventId\":\"e-null\","
                + "\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-1\","
                + "\"gameType\":\"CHESS\","
                + "\"durationSeconds\":null,"
                + "\"status\":\"COMPLETED\"}";
        OutboxEventDto event = new OutboxEventDto("e-null", "GAME_SESSION_COMPLETED", payload, Instant.now());

        when(processedEventRepository.existsByEventId("e-null")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                any(BuildingId.class), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processOne(new BuildingId("building-1"), event);

        assertThat(result).isTrue();

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository).save(captor.capture());
        AggregatedStatistics saved = captor.getValue();
        assertThat(saved.getAvgDurationSeconds())
                .as("extractDuration must fall back to 0 when durationSeconds is null")
                .isZero();
        assertThat(saved.getTotalSessions())
                .as("COMPLETED session always increments totalSessions by 1 (count invariant)")
                .isEqualTo(1);

        boolean warnLogged = listAppender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("durationSeconds")
                        && e.getFormattedMessage().contains("null"));
        assertThat(warnLogged)
                .as("extractDuration must log a WARN identifying the null durationSeconds value")
                .isTrue();
    }

    @Test
    @DisplayName("S2: {\"durationSeconds\": \"abc\"} logs WARN ('non-numeric') and falls back to 0; totalSessions still incremented")
    void nonNumericDurationLogsWarnAndFallsBackToZero() throws Exception {
        String payload = "{\"eventId\":\"e-abc\","
                + "\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-2\","
                + "\"gameType\":\"CHESS\","
                + "\"durationSeconds\":\"abc\","
                + "\"status\":\"COMPLETED\"}";
        OutboxEventDto event = new OutboxEventDto("e-abc", "GAME_SESSION_COMPLETED", payload, Instant.now());

        when(processedEventRepository.existsByEventId("e-abc")).thenReturn(false);
        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(
                any(BuildingId.class), eq(GameType.CHESS), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(statisticsRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        boolean result = processor.processOne(new BuildingId("building-1"), event);

        assertThat(result).isTrue();

        ArgumentCaptor<AggregatedStatistics> captor = ArgumentCaptor.forClass(AggregatedStatistics.class);
        verify(statisticsRepository).save(captor.capture());
        AggregatedStatistics saved = captor.getValue();
        assertThat(saved.getAvgDurationSeconds())
                .as("extractDuration must fall back to 0 when durationSeconds is non-numeric")
                .isZero();
        assertThat(saved.getTotalSessions())
                .as("COMPLETED session always increments totalSessions by 1 (count invariant)")
                .isEqualTo(1);

        boolean warnLogged = listAppender.list.stream()
                .anyMatch(e -> e.getLevel() == Level.WARN
                        && e.getFormattedMessage().contains("durationSeconds")
                        && e.getFormattedMessage().contains("non-numeric"));
        assertThat(warnLogged)
                .as("extractDuration must log a WARN identifying the non-numeric durationSeconds value")
                .isTrue();
    }
}