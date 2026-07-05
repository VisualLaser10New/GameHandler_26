package com.gameplatform.local.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.OutboxEventJpaRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final long RETENTION_DAYS = 7L;

    @Mock
    OutboxEventJpaRepository outboxEventJpaRepository;

    private OutboxPurgeService service;

    @BeforeEach
    void setup() {
        service = new OutboxPurgeService(
                outboxEventJpaRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                RETENTION_DAYS
        );
    }

    @Test
    void purgeDeletesSentEventsOlderThanRetention() {
        Instant expectedCutoff = NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS);
        when(outboxEventJpaRepository.deleteSentOlderThan(eq(expectedCutoff))).thenReturn(5);

        service.purgeOldSentEvents();

        verify(outboxEventJpaRepository, times(1)).deleteSentOlderThan(eq(expectedCutoff));
    }

    @Test
    void purgeNoOpWhenNoOldEvents() {
        Instant expectedCutoff = NOW.minus(RETENTION_DAYS, ChronoUnit.DAYS);
        when(outboxEventJpaRepository.deleteSentOlderThan(eq(expectedCutoff))).thenReturn(0);

        service.purgeOldSentEvents();

        verify(outboxEventJpaRepository, times(1)).deleteSentOlderThan(eq(expectedCutoff));
        assertThat(outboxEventJpaRepository.deleteSentOlderThan(expectedCutoff)).isEqualTo(0);
    }
}
