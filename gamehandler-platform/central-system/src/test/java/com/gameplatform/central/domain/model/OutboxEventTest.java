package com.gameplatform.central.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxEventTest {

    @Test
    void shouldCreateOutboxEventSuccessfullyWhenInputsAreValid() {
        Instant now = Instant.now();
        OutboxEvent event = new OutboxEvent("event-1", "USER_REGISTERED", "{\"user\":\"john\"}", OutboxEventStatus.PENDING, now, null);

        assertThat(event.getId()).isEqualTo("event-1");
        assertThat(event.getEventType()).isEqualTo("USER_REGISTERED");
        assertThat(event.getPayload()).isEqualTo("{\"user\":\"john\"}");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getCreatedAt()).isEqualTo(now);
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void shouldThrowExceptionWhenConstructorInputsAreInvalid() {
        Instant now = Instant.now();

        // Null/empty/blank ID
        assertThatThrownBy(() -> new OutboxEvent(null, "EVENT", "payload", OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent("", "EVENT", "payload", OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank EventType
        assertThatThrownBy(() -> new OutboxEvent("id", null, "payload", OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent("id", "  ", "payload", OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Null/empty/blank Payload
        assertThatThrownBy(() -> new OutboxEvent("id", "EVENT", null, OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutboxEvent("id", "EVENT", "   ", OutboxEventStatus.PENDING, now, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Null status
        assertThatThrownBy(() -> new OutboxEvent("id", "EVENT", "payload", null, now, null))
                .isInstanceOf(IllegalArgumentException.class);

        // Null createdAt
        assertThatThrownBy(() -> new OutboxEvent("id", "EVENT", "payload", OutboxEventStatus.PENDING, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldMarkAsSent() {
        OutboxEvent event = new OutboxEvent("event-1", "USER_REGISTERED", "{}", OutboxEventStatus.PENDING, Instant.now(), null);
        
        event.markAsSent();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getSentAt()).isAfterOrEqualTo(event.getCreatedAt());
    }
}
