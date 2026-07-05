package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.SyncPayloadDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Regression test for Bug L-05: SyncSchedulerService used to call
 * {@code markAsSent} once per event id in a loop, without transactional atomicity.
 * A failure mid-loop left some events SENT and others PENDING (partial update).
 *
 * <p>Fix: {@link OutboxSyncHelper} now delegates to
 * {@link OutboxEventRepository#markAsSentBatch(List)} / {@link OutboxEventRepository#incrementRetryBatch(List)}
 * which execute a single bulk UPDATE statement inside one transaction. This test
 * verifies the scheduler triggers exactly one batch call (atomic by construction)
 * instead of N per-id calls.</p>
 */
class BugL05_SyncSchedulerNonAtomicMarkAsSentTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private SyncCentralSystemPort syncCentralSystemPort;

    private SyncSchedulerService syncSchedulerService;

    private static final Instant NOW = Instant.parse("2026-06-29T08:00:00Z");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        syncSchedulerService = new SyncSchedulerService(
                outboxEventRepository,
                syncCentralSystemPort,
                new OutboxSyncHelper(outboxEventRepository),
                "building-1"
        );
    }

    @Test
    @DisplayName("FIX L-05: successful sync issues a single atomic markAsSentBatch call (not N per-id calls)")
    void successfulSyncUsesSingleAtomicBatchMarkAsSent() {
        OutboxEvent event1 = new OutboxEvent("evt-1", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event2 = new OutboxEvent("evt-2", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event3 = new OutboxEvent("evt-3", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event4 = new OutboxEvent("evt-4", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);

        when(outboxEventRepository.findPending()).thenReturn(List.of(event1, event2, event3, event4));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(true);

        syncSchedulerService.syncWithCentral();

        // Exactly ONE batch call with all 4 ids — atomic by construction (single UPDATE).
        verify(outboxEventRepository, times(1)).markAsSentBatch(List.of("evt-1", "evt-2", "evt-3", "evt-4"));
        // The old per-id API must NOT be used anymore.
        verify(outboxEventRepository, never()).markAsSent(any());
        verify(outboxEventRepository, never()).incrementRetry(any());
        verify(outboxEventRepository, never()).incrementRetryBatch(any());
    }

    @Test
    @DisplayName("FIX L-05: failed sync issues a single atomic incrementRetryBatch call")
    void failedSyncUsesSingleAtomicBatchIncrementRetry() {
        OutboxEvent event1 = new OutboxEvent("evt-1", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event2 = new OutboxEvent("evt-2", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);

        when(outboxEventRepository.findPending()).thenReturn(List.of(event1, event2));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(false);

        syncSchedulerService.syncWithCentral();

        verify(outboxEventRepository, times(1)).incrementRetryBatch(List.of("evt-1", "evt-2"));
        verify(outboxEventRepository, never()).markAsSentBatch(any());
        verify(outboxEventRepository, never()).incrementRetry(any());
    }
}
