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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FASE 4 — step 1 "stress + idempotency" proof for the duplicate-event contract
 * (per_event PK on processed_events + existsByEventId check in
 * {@link SyncEventProcessor#processOne}).
 *
 * <p>Pure unit test (no Spring context). {@link SyncReceiverService} is built
 * against a Mockito-mocked {@link ProcessedEventRepository} whose
 * {@code existsByEventId} / {@code save} are backed by a thread-safe
 * {@code Set} emulating the real {@code processed_events} per_event PK and
 * the unique-constraint throw on duplicate inserts.</p>
 *
 * <p>Two complementary scenarios:</p>
 * <ul>
 *   <li><b>singleThread100SequentialDuplicatesProcessesOnce</b> — drives the
 *       exact-once assertion: the first of 100 sequential calls populates the
 *       fake persisted-event set and the remaining 99 are short-circuited by
 *       {@code existsByEventId}. {@code statisticsRepository.save} is exercised
 *       exactly once.</li>
 *   <li><b>multiThreadStressNoBatchAbortNorHang</b> — submits 100 duplicate
 *       receiveSyncPayload tasks to a 10-thread pool. The mock has a TOCTOU
 *       window between {@code existsByEventId} and {@code save} that the real
 *       DB unique-constraint closes; therefore exact-once is NOT asserted
 *       (the count can be &gt;1 in this multithreaded unit setup). Instead we
 *       assert: every task completes without throwing (no batch abort) and
 *       the run finishes within 15s (no deadlock / hang). This is the
 *       "no-batch-abort, no-hang" stress proof required by FASE 4 step 1;
 *       exactly-once is enforced at the DB layer (per_event PK +
 *       {@link DataIntegrityViolationException} caught in
 *       {@link SyncEventProcessor#processOne}).</li>
 * </ul>
 *
 * <p><b>Choice rationale</b>: the multi-thread variant was kept as a noise /
 * no-hang proof, but exact-once is asserted only by the single-thread variant
 * because the mock-backed {@code existsByEventId} check is non-atomic w.r.t.
 * the mock-backed {@code save}, producing a flaky {@code statsSaveCount == 1}
 * assertion in a racing context. The single-thread path emits a deterministic
 * "seenEventIds first false, then true" sequence that emulates the post-commit
 * DB state.
 */
@ExtendWith(MockitoExtension.class)
class SyncReceiverConcurrencyStressTest {

    private static final String EVENT_ID = "evt-X";
    private static final String BUILDING_ID = "building-stress";
    private static final String PAYLOAD =
            "{\"gameType\":\"CHESS\",\"occurredAt\":\"2026-07-05T12:00:00Z\",\"durationSeconds\":120}";

    @Mock private StatisticsRepository statisticsRepository;
    @Mock private LocalServerRegistryPort localServerRegistryPort;
    @Mock private RegisterUserFromSyncUseCase registerUserFromSyncUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxEventDto event() {
        return new OutboxEventDto(EVENT_ID, "GAME_SESSION_COMPLETED", PAYLOAD, Instant.now());
    }

    private SyncPayloadDto payload() {
        return new SyncPayloadDto(BUILDING_ID, List.of(event()));
    }

    /**
     * Builds a Mockito-mock {@link ProcessedEventRepository} whose
     * {@code existsByEventId}/{@code save} mirror a {@code processed_events}
     * table with {@code per_event PK}: {@code save} throws
     * {@link DataIntegrityViolationException} when the eventId is already in
     * the supplied {@code seen} set, exactly like the real DB does.
     *
     * <p>Stubs are {@code lenient()} because the standalone mock is not
     * strictly tracked by {@link MockitoExtension} and we want robustness
     * regardless of which test path is exercised.</p>
     */
    private ProcessedEventRepository fakeProcessedEventRepository(Set<String> seen) {
        ProcessedEventRepository repo = mock(ProcessedEventRepository.class);
        lenient().when(repo.existsByEventId(anyString()))
                .thenAnswer(inv -> seen.contains(inv.<String>getArgument(0)));
        lenient().doAnswer(inv -> {
            ProcessedEvent pe = inv.getArgument(0);
            if (!seen.add(pe.getEventId())) {
                throw new DataIntegrityViolationException("DUPLICATE_EVENT_ID");
            }
            return null;
        }).when(repo).save(any(ProcessedEvent.class));
        return repo;
    }

    @Test
    void singleThread100SequentialDuplicatesProcessesOnce() {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ProcessedEventRepository processedEventRepository = fakeProcessedEventRepository(seen);

        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());

        AtomicInteger statsSaveCount = new AtomicInteger();
        doAnswer(inv -> {
            statsSaveCount.incrementAndGet();
            return inv.getArgument(0);
        }).when(statisticsRepository).save(any());

        SyncReceiverService service = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                registerUserFromSyncUseCase,
                objectMapper);

        for (int i = 0; i < 100; i++) {
            service.receiveSyncPayload(payload());
        }

        assertThat(statsSaveCount.get())
                .as("stats.save called exactly once across 100 sequential duplicates (idempotency via existsByEventId)")
                .isEqualTo(1);
        verify(localServerRegistryPort, times(100)).updateLastSeenAt(any(), any());
    }

    @Test
    void multiThreadStressNoBatchAbortNorHang() throws Exception {
        Set<String> seen = ConcurrentHashMap.newKeySet();
        ProcessedEventRepository processedEventRepository = fakeProcessedEventRepository(seen);

        when(statisticsRepository.findByBuildingAndTypeAndPeriodWithLock(any(), any(), any()))
                .thenReturn(Optional.empty());

        AtomicInteger statsSaveCount = new AtomicInteger();
        doAnswer(inv -> {
            statsSaveCount.incrementAndGet();
            return inv.getArgument(0);
        }).when(statisticsRepository).save(any());

        SyncReceiverService service = new SyncReceiverService(
                processedEventRepository,
                statisticsRepository,
                localServerRegistryPort,
                registerUserFromSyncUseCase,
                objectMapper);

        int taskCount = 100;
        int poolSize = 10;
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CountDownLatch done = new CountDownLatch(taskCount);
        AtomicReference<Throwable> firstErr = new AtomicReference<>();

        try {
            for (int i = 0; i < taskCount; i++) {
                pool.submit(() -> {
                    try {
                        service.receiveSyncPayload(payload());
                    } catch (Throwable t) {
                        firstErr.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }
            boolean finished = done.await(15, TimeUnit.SECONDS);
            assertThat(finished)
                    .as("100 concurrent duplicate tasks finished in <15s (no deadlock / hang)")
                    .isTrue();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(firstErr.get())
                .as("No task threw — duplicate DataIntegrityViolationException was caught in SyncEventProcessor.processOne (no batch abort)")
                .isNull();
        assertThat(statsSaveCount.get())
                .as("At least one stats save happened across the duplicate run (exactly-once is enforced at the DB layer; mock TOCTOU may over-count)")
                .isGreaterThanOrEqualTo(1);
    }
}
