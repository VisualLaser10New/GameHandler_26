package com.gameplatform.central.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessedEventTest {

    @Test
    void shouldCreateProcessedEventSuccessfullyWhenInputsAreValid() {
        Instant now = Instant.now();
        ProcessedEvent event = new ProcessedEvent("event-123", now);

        assertThat(event.getEventId()).isEqualTo("event-123");
        assertThat(event.getProcessedAt()).isEqualTo(now);
    }

    @Test
    void shouldThrowExceptionWhenConstructorInputsAreInvalid() {
        assertThatThrownBy(() -> new ProcessedEvent(null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessedEvent("   ", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldImplementEqualsAndHashCodeBasedOnEventId() {
        Instant t1 = Instant.now();
        Instant t2 = Instant.now().plusSeconds(5);

        ProcessedEvent event1 = new ProcessedEvent("event-1", t1);
        ProcessedEvent event2 = new ProcessedEvent("event-1", t2);
        ProcessedEvent event3 = new ProcessedEvent("event-2", t1);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1).isNotEqualTo(event3);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
        assertThat(event1.hashCode()).isNotEqualTo(event3.hashCode());
    }
}
