package com.gameplatform.local.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    @Test
    void shouldCreateOutboxEventSuccessfully() {
        Instant created = Instant.now();
        OutboxEvent event = new OutboxEvent("evt-1", "USER_REGISTERED", "{\"username\":\"john\"}", "PENDING", created, null, 0);

        assertEquals("evt-1", event.getId());
        assertEquals("USER_REGISTERED", event.getEventType());
        assertEquals("{\"username\":\"john\"}", event.getPayload());
        assertEquals("PENDING", event.getStatus());
        assertEquals(created, event.getCreatedAt());
        assertNull(event.getSentAt());
        assertEquals(0, event.getRetryCount());
    }

    @Test
    void shouldMarkAsSent() {
        Instant created = Instant.now();
        OutboxEvent event = new OutboxEvent("evt-1", "USER_REGISTERED", "{}", "PENDING", created, null, 0);

        Instant sent = Instant.now().plusSeconds(10);
        event.markAsSent(sent);

        assertEquals("SENT", event.getStatus());
        assertEquals(sent, event.getSentAt());
    }

    @Test
    void shouldIncrementRetryAndMarkFailedIfThresholdReached() {
        Instant created = Instant.now();
        OutboxEvent event = new OutboxEvent("evt-1", "USER_REGISTERED", "{}", "PENDING", created, null, 0);

        // First retry
        event.incrementRetry();
        assertEquals(1, event.getRetryCount());
        assertFalse(event.hasFailed());
        assertEquals("PENDING", event.getStatus());

        // Second retry
        event.incrementRetry();
        assertEquals(2, event.getRetryCount());
        assertFalse(event.hasFailed());
        assertEquals("PENDING", event.getStatus());

        // Third retry -> Should fail
        event.incrementRetry();
        assertEquals(3, event.getRetryCount());
        assertTrue(event.hasFailed());
        assertEquals("FAILED", event.getStatus());
    }
}
