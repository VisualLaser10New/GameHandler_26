package com.gameplatform.central.integration;

import com.gameplatform.central.domain.ports.in.ReceiveSyncDataUseCase;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * S3 / C-R1 — first-bucket {@code aggregated_statistics} insert race (concurrency IT).
 *
 * <p>Two {@code GAME_SESSION_COMPLETED} events with DISTINCT {@code eventId}s
 * (so each passes the {@code processed_events} dedup check) but the SAME
 * {@code (building, CHESS, 2026-07-05)} bucket are processed concurrently by two
 * threads. The first-bucket path is the only branch that does not first acquire a
 * pessimistic write lock on an existing row — both threads see the bucket absent
 * and both attempt an INSERT guarded by the unique constraint
 * {@code uk_building_type_period}.</p>
 *
 * <p>Contract asserted (post-S3-fix):
 * <ul>
 *   <li>{@code aggregated_statistics.total_sessions == 2} — neither session lost
 *       (no lost-update) nor double-counted. One thread wins the INSERT; the
 *       other receives a {@link org.springframework.dao.DataIntegrityViolationException}
 *       at the {@code statisticsRepository.save(newStats)} call site (made reachable
 *       by the adapter's {@code saveAndFlush}), and retries by re-reading the
 *       winner with the pessimistic lock and merging.</li>
 *   <li>Both {@code eventId}s are present in {@code processed_events} — exactly-once
 *       processing per event id (dedup PK).</li>
 *   <li>No exception propagates to the caller (the catch+merge is contained inside
 *       {@code SyncEventProcessor.processOne}).</li>
 * </ul>
 *
 * <p><b>RED before fix (S3):</b> without the {@code saveAndFlush} + DIVE catch +
 * merge, the loser thread's INSERT would either be deferred to tx-commit (raising
 * {@link org.springframework.dao.DataIntegrityViolationException} outside the
 * application service's reachable try-catch, propagated as a poison event with
 * stats lost) or — under MVCC — both INSERTs would race and one tx would be rolled
 * back, leaving {@code total_sessions == 1}.</p>
 *
 * <p><b>H2 vs InnoDB documentation:</b> the production target is MySQL/InnoDB, where
 * a concurrent INSERT hitting a unique-key conflict raises the duplicate-key error
 * AT INSERT time while the losing transaction is still active — which is exactly
 * the reachable site where {@code saveAndFlush} surfaces the {@link
 * org.springframework.dao.DataIntegrityViolationException}. H2 (test profile,
 * MODE=MySQL) supports {@code SELECT … FOR UPDATE} (PESSIMISTIC_WRITE) and enforces
 * unique constraints at INSERT execution; the loser's {@code saveAndFlush} raises
 * the violation inside the application service's try-catch, so the merge-retry path
 * is reachable. If a future H2 version defers UK enforcement to COMMIT time, this
 * IT should be re-evaluated against the unit tests
 * {@code SyncEventProcessorFirstBucketRetryMergeUnitTest} (deterministic, mock-driven
 * assertion of the same retry contract).</p>
 */
@DisplayName("S3 / C-R1: first-bucket aggregated_statistics insert race (concurrency)")
class AggregatedStatisticsFirstBucketInsertRaceConcurrencyIT extends ContractTestBase {

    @Autowired
    ReceiveSyncDataUseCase receiveSyncDataUseCase;

    @Test
    @DisplayName("Two concurrent GAME_SESSION_COMPLETED with same (building, CHESS, period) → totalSessions==2, both eventIds processed, no exception")
    void twoConcurrentCompletedEventsWithSameBucketAggregateCorrectly() throws Exception {
        when(localServerRegistryPort.getActiveLocalServers()).thenReturn(List.of());

        String eventIdA = UUID.randomUUID().toString();
        String eventIdB = UUID.randomUUID().toString();
        String payloadA = completedPayload(eventIdA, 120);
        String payloadB = completedPayload(eventIdB, 240);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicReference<Throwable> firstErr = new AtomicReference<>();

        try {
            pool.submit(() -> runOne(ready, start, done, firstErr, eventIdA, payloadA));
            pool.submit(() -> runOne(ready, start, done, firstErr, eventIdB, payloadB));
            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("both threads reached the start barrier")
                    .isTrue();
            start.countDown(); // release both threads simultaneously
            boolean finished = done.await(30, TimeUnit.SECONDS);
            assertThat(finished)
                    .as("both concurrent events finished in <30s (no deadlock / hang)")
                    .isTrue();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }

        assertThat(firstErr.get())
                .as("No task threw — first-bucket insert race resolved by DIVE catch + merge inside processOne")
                .isNull();

        Integer totalSessions = jdbcTemplate.queryForObject(
                "SELECT total_sessions FROM aggregated_statistics WHERE building_id='building-test' AND game_type='CHESS'",
                Integer.class);
        assertThat(totalSessions)
                .as("both completed sessions counted (no lost update, no double-count, merge path taken on race)")
                .isEqualTo(2);

        Integer processedA = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, eventIdA);
        Integer processedB = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processed_events WHERE event_id=?", Integer.class, eventIdB);
        assertThat(processedA).as("eventId A marked processed (exactly-once per event id)").isEqualTo(1);
        assertThat(processedB).as("eventId B marked processed (exactly-once per event id)").isEqualTo(1);
    }

    private void runOne(CountDownLatch ready, CountDownLatch start, CountDownLatch done,
                        AtomicReference<Throwable> firstErr, String eventId, String payload) {
        try {
            ready.countDown();
            start.await();
            OutboxEventDto event = new OutboxEventDto(eventId, "GAME_SESSION_COMPLETED", payload, Instant.now());
            SyncPayloadDto batch = new SyncPayloadDto("building-test", List.of(event));
            receiveSyncDataUseCase.receiveSyncPayload(batch);
        } catch (Throwable t) {
            firstErr.compareAndSet(null, t);
        } finally {
            done.countDown();
        }
    }

    private static String completedPayload(String eventId, int duration) {
        return "{\"eventId\":\"" + eventId + "\","
                + "\"occurredAt\":\"2026-07-05T12:00:00Z\","
                + "\"sessionId\":\"s-" + eventId + "\","
                + "\"gameType\":\"CHESS\","
                + "\"durationSeconds\":" + duration + ","
                + "\"status\":\"COMPLETED\",\"resultJson\":null}";
    }
}
