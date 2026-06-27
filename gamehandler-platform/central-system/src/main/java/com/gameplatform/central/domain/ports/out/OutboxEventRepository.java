package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
    /** Returns at most {@code limit} pending events, ordered by creation time ascending. */
    List<OutboxEvent> findPendingLimit(int limit);
    void markAsSent(String id);
}

