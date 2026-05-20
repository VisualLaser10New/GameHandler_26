package com.gameplatform.shared.domain.events;

import java.time.Instant;

public interface DomainEvent {
    String getEventId();
    Instant getOccurredAt();
    String getEventType();
}
