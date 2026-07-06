package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
    /** Returns at most {@code limit} pending events, ordered by creation time ascending. */
    List<OutboxEvent> findPendingLimit(int limit);
    void markAsSent(String id);
    void markAsFailed(String id);

    /**
     * M12 — counts the number of user-replication events
     * ({@code USER_REGISTERED} / {@code USER_UPDATED}) whose status is NOT
     * {@code SENT} and for which no {@code replication_progress} row has been
     * recorded for the given server. This is the "pending replication backlog"
     * surfaced per-server in the admin {@code /internal/servers} health view.
     *
     * @param serverId the building id of the local server
     * @return a non-negative count
     */
    long countPendingReplicationForServer(String serverId);
}

