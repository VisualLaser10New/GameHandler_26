package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.OutboxEvent;
import java.util.List;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
    List<OutboxEvent> findPending();
    void markAsSent(String id);
}

