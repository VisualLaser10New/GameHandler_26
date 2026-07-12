package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for the {@code player_statistics} read-model table (FASE 3,
 * PIANO &sect;2.3). Aggregated per-player, per-game-type counters kept in sync
 * with {@code player_match_facts} by the {@code SyncEventProcessor} projection.
 *
 * <p>{@link #increment} encapsulates the atomic, race-safe counter increment:
 * the adapter implementation acquires a {@code PESSIMISTIC_WRITE} lock on the
 * existing row before merging, and resolves the first-row insert race (two
 * concurrent events for a brand-new (user, gameType)) internally &mdash; via the
 * EntityManager directly, so the constraint violation is caught before it can
 * cross a transactional boundary and the caller's transaction is never marked
 * rollback-only. This mirrors the {@code aggregated_statistics}
 * {@code findBy...WithLock} pessimistic-lock pattern (PIANO &sect;2.4 /
 * protocol &sect;2.C thread-safety mandate).</p>
 */
public interface PlayerStatisticsRepository {

    /** All statistics rows for the given user (empty list if the user has played nothing). */
    List<PlayerStatistics> findByUserId(UserId userId);

    /** The single statistics row for (user, gameType), if any. */
    Optional<PlayerStatistics> findByUserIdAndGameType(UserId userId, GameType gameType);

    /**
     * Atomically records one additional completed match for (user, gameType):
     * {@code matchesPlayed += 1}, {@code matchesWon += (won ? 1 : 0)} and
     * {@code lastPlayedAt = max(existing, endedAt)}. Race-safe under a
     * pessimistic write lock; must be invoked within an active transaction.
     */
    void increment(UserId userId, GameType gameType, boolean won, java.time.Instant endedAt);
}