package com.gameplatform.e2e.localcentral;

import com.gameplatform.e2e.harness.DualContextTestBase;
import com.gameplatform.local.domain.exception.ConcurrentStateException;
import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.shared.domain.model.GameId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * B17 — POF-5 optimistic locking under real concurrency on the local H2
 * database (embedded Moquette broker is shared via {@link DualContextTestBase}).
 *
 * <p><b>Why this is deterministic and not flaky:</b> the service-level
 * {@code GameSessionService.start(...)} uses
 * {@code GameRepositoryAdapter.findByIdForUpdate} which is a
 * {@code @Lock(PESSIMISTIC_WRITE)} — concurrent starts of the same game-machine
 * would SERIALISE on the row lock and the loser would read {@code IN_USE} and
 * raise a domain {@code InvalidGameStateTransitionException}, not a
 * {@link ConcurrentStateException}. That outcome is timing-dependent and therefore
 * flaky. Instead, per the task's accepted fallback, this test exercises the
 * optimistic-lock {@code saveAndFlush} path directly via the
 * {@link GameRepository} adapter.</p>
 *
 * <p>The version is pre-loaded into two separate domain {@link Game} instances
 * (both {@code version == 1}) <i>before</i> the concurrent saves. The loser's
 * entity therefore carries {@code version = 1} regardless of when the winner
 * commits, so the loser's {@code merge -> UPDATE ... WHERE version = 1} always
 * finds zero rows (the winner has bumped it to {@code 2}) and raises a Spring
 * {@code OptimisticLockingFailureException}, which the adapter translates to
 * {@link ConcurrentStateException}. This holds even if one thread fully commits
 * before the other starts, so the test has no timing assumption.</p>
 *
 * <p>The game is seeded with {@code version = 1} (not {@code 0}) on purpose: the
 * {@code GameMapper.toEntity} only threads the version onto the JPA entity when
 * {@code domain.version > 0}, which is exactly the precondition for
 * {@code saveAndFlush} to take the {@code merge}/UPDATE-with-version-check path
 * that the optimistic guard relies on.</p>
 */
@DisplayName("B17: concurrent game-machine saves lose the optimistic lock deterministically")
class B17ConcurrentGameMachineStartOptimisticLockTest extends DualContextTestBase {

    private static final String GAME_ID = "game-optlock-1";

    @Test
    @DisplayName("two concurrent saves with the same pre-loaded version: exactly one wins, the other throws ConcurrentStateException, version increments in the DB")
    void concurrentSavesOneWinsOneThrowsConcurrentState() throws Exception {
        localJdbcTemplate.update(
                "INSERT INTO game_catalog (id, game_type, name, building_id, status, version) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                GAME_ID, "CHESS", "Optlock Table", "building-1", "AVAILABLE", 1);

        GameRepository repository = localBean(GameRepository.class);

        // Pre-load two independent domain instances, both carrying version == 1.
        Game gameA = repository.findById(new GameId(GAME_ID)).orElseThrow(
                () -> new IllegalStateException("seeded game not found"));
        Game gameB = repository.findById(new GameId(GAME_ID)).orElseThrow(
                () -> new IllegalStateException("seeded game not found"));
        assertThat(gameA.getVersion()).isEqualTo(1L);
        assertThat(gameB.getVersion()).isEqualTo(1L);

        // Dirty both so Hibernate actually issues an UPDATE (not a no-op).
        gameA.setMaintenance();
        gameB.setMaintenance();

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Game>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final Game game = (i == 0) ? gameA : gameB;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.save(game);
                }));
            }

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();

            int successes = 0;
            int concurrentStateFailures = 0;
            AtomicReference<Throwable> unexpectedFailure = new AtomicReference<>();
            for (Future<Game> f : futures) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                    successes++;
                } catch (Exception e) {
                    Throwable cause = (e.getCause() != null) ? e.getCause() : e;
                    if (cause instanceof ConcurrentStateException) {
                        concurrentStateFailures++;
                    } else {
                        unexpectedFailure.set(cause);
                    }
                }
            }

            assertThat(unexpectedFailure.get()).as("no unexpected (non-ConcurrentStateException) failure").isNull();
            assertThat(successes).as("exactly one save wins").isEqualTo(1);
            assertThat(concurrentStateFailures).as("exactly one save loses the optimistic lock")
                    .isEqualTo(1);

            Long versionAfter = localJdbcTemplate.queryForObject(
                    "SELECT version FROM game_catalog WHERE id = ?",
                    Long.class, GAME_ID);
            assertThat(versionAfter).as("version increments past the seed (1 -> 2)").isEqualTo(2L);

            Integer sessionRows = localJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM game_sessions WHERE game_id = ?",
                    Integer.class, GAME_ID);
            assertThat(sessionRows).as("adapter-level save never creates a session row").isZero();
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}