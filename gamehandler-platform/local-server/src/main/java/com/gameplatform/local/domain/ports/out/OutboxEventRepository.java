package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
    void markAsSent(String id);
    void incrementRetry(String id);

    /**
     * Atomically marks all the given event ids as SENT in a single bulk UPDATE.
     * Equivalent to calling {@link #markAsSent(String)} for each id but executed
     * as one SQL statement, avoiding the N read-modify-write roundtrips and
     * guaranteeing atomicity within the caller's transaction.
     *
     * @param ids event ids currently in PENDING status; ids not found or not
     *            PENDING are silently skipped by the UPDATE filter.
     */
    void markAsSentBatch(List<String> ids);

    /**
     * Atomically increments the retry counter for the given event ids in a single
     * bulk UPDATE. Events reaching the retry threshold transition to FAILED status
     * inside the same statement (the domain rule is replicated in the JPQL query
     * to keep the bulk update consistent with {@link #incrementRetry(String)}).
     */
    void incrementRetryBatch(List<String> ids);
}
