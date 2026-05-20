package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.ProcessedEvent;

public interface ProcessedEventRepository {
    boolean existsByEventId(String eventId);
    void save(ProcessedEvent event);
}

