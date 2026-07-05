package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.domain.ports.out.DeadLetterRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxDlqPromotionTest {

    @Mock
    OutboxEventJpaRepository outboxJpaRepository;

    @Mock
    DeadLetterRepository deadLetterRepository;

    private OutboxDlqPromotionService service;

    @BeforeEach
    void setup() {
        service = new OutboxDlqPromotionService(
                outboxJpaRepository,
                deadLetterRepository,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private OutboxEventJpaEntity failedEntity(String id) {
        return new OutboxEventJpaEntity(
                id,
                "RESERVATION_CREATED",
                "{\"k\":\"v\"}",
                "FAILED",
                Instant.parse("2026-01-01T00:00:00Z"),
                null,
                10
        );
    }

    @Test
    void shouldDoNothingWhenNoFailedEvents() {
        when(outboxJpaRepository.findByStatusOrderByCreatedAtAsc("FAILED")).thenReturn(List.of());

        service.promoteFailedToDlq();

        verify(deadLetterRepository, never()).save(any());
        verify(outboxJpaRepository, never()).delete(any());
        verify(outboxJpaRepository, never()).deleteById(any());
    }

    @Test
    void shouldPromoteTwoFailedEventsToDlqAndDeleteThem() {
        OutboxEventJpaEntity e1 = failedEntity("e-1");
        OutboxEventJpaEntity e2 = failedEntity("e-2");
        when(outboxJpaRepository.findByStatusOrderByCreatedAtAsc("FAILED")).thenReturn(List.of(e1, e2));
        when(deadLetterRepository.count()).thenReturn(2L);

        service.promoteFailedToDlq();

        ArgumentCaptor<DeadLetterEvent> captor = ArgumentCaptor.forClass(DeadLetterEvent.class);
        verify(deadLetterRepository, times(2)).save(captor.capture());
        List<DeadLetterEvent> saved = captor.getAllValues();
        assertEquals(2, saved.size());

        DeadLetterEvent first = saved.get(0);
        assertEquals("e-1", first.getId());
        assertEquals("e-1", first.getEventId());
        assertEquals("RESERVATION_CREATED", first.getEventType());
        assertEquals("{\"k\":\"v\"}", first.getPayload());
        assertEquals("FAILED", first.getOriginalStatus());
        assertEquals(10, first.getRetryCount());
        assertEquals("RETRY_THRESHOLD_EXCEEDED", first.getReason());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), first.getPromotedAt());

        assertEquals("e-2", saved.get(1).getId());

        verify(outboxJpaRepository).delete(e1);
        verify(outboxJpaRepository).delete(e2);
        verify(deadLetterRepository, times(1)).count();
    }
}
