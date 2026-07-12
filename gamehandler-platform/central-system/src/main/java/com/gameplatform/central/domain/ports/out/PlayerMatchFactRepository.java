package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.PlayerMatchFact;

/**
 * Persistence port for the {@code player_match_facts} read-model table (FASE 3,
 * PIANO &sect;2.3). One row per (session, participant).
 *
 * <p>Persisted by the {@code SyncEventProcessor} projection when a
 * {@code GAME_SESSION_COMPLETED} event is consumed. The composite primary key
 * {@code (session_id, user_id)} makes each fact naturally idempotent, so the
 * projection exposes a single {@link #saveIfAbsent} operation: it inserts the
 * fact and reports whether the row was newly created, swallowing the
 * duplicate-key race internally (the adapter never lets the constraint
 * violation cross a transactional boundary, so the caller's transaction is not
 * poisoned).</p>
 */
public interface PlayerMatchFactRepository {

    /**
     * Inserts the given player match fact if no fact yet exists for its
     * {@code (sessionId, userId)} pair.
     *
     * @return {@code true} if the row was newly inserted; {@code false} if a
     *         fact for the same (sessionId, userId) pair already existed (the
     *         call is a no-op idempotent retry in that case)
     */
    boolean saveIfAbsent(PlayerMatchFact fact);
}