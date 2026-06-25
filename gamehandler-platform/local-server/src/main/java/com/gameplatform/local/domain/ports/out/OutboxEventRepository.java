package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
    void markAsSent(String id);
    void incrementRetry(String id);
}
