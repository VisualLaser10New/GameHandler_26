package com.gameplatform.local.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.SyncPayloadDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression test for POF-7 / Bug-L07: local outbox sync starvation.
 *
 * <p>The scheduler fetches a bounded batch via {@link OutboxEventRepository#findPendingLimit(int)}
 * (no more unbounded {@code findPending()} fetches that could starve under load) and,
 * on batch-transport failure, falls back to per-event retry so that a single poison
 * event cannot abort the whole batch (poison isolation). Good per-event sends are
 * marked SENT individually; only the poison is retried.</p>
 */
@DisplayName("Bug L-07: local outbox sync starvation + poison isolation")
@ExtendWith(MockitoExtension.class)
class BugL07_SyncStarvationPoisonIsolationTest {

    @Mock OutboxEventRepository outboxEventRepository;
    @Mock SyncCentralSystemPort syncCentralSystemPort;

    private SyncSchedulerService service;

    @BeforeEach
    void setup() {
        service = new SyncSchedulerService(
                outboxEventRepository,
                syncCentralSystemPort,
                new OutboxSyncHelper(outboxEventRepository),
                "building-1",
                50
        );
    }

    private OutboxEvent event(String id) {
        return new OutboxEvent(id, "GAME_SESSION_COMPLETED", "{}", "PENDING", Instant.now(), null, 0);
    }

    @Test
    @DisplayName("poison event retries alone does not nuke the whole batch — loop continues past a per-event failure")
    void poisonEventRetriesAloneDoesNotNukeWholeBatch() {
        when(outboxEventRepository.findPendingLimit(anyInt()))
                .thenReturn(List.of(event("e-1"), event("e-2"), event("e-3")));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        // 4 calls in order: 1 batch (fails) + 3 per-event (e-1 ok, e-2 poison, e-3 ok).
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class)))
                .thenReturn(false)   // batch transport fails
                .thenReturn(true)    // e-1 per-event ok
                .thenReturn(false)   // e-2 poison
                .thenReturn(true);   // e-3 per-event ok

        service.syncWithCentral();

        // Good per-event sends are marked SENT individually.
        verify(outboxEventRepository).markAsSent("e-1");
        verify(outboxEventRepository).markAsSent("e-3");
        // The poison event is retried.
        verify(outboxEventRepository).incrementRetry("e-2");
        // Good events are NOT retried.
        verify(outboxEventRepository, never()).incrementRetry("e-1");
        verify(outboxEventRepository, never()).incrementRetry("e-3");
        // No atomic batch markAsSent on the failure path.
        verify(outboxEventRepository, never()).markAsSentBatch(any());
    }

    @Test
    @DisplayName("unbounded findPending() fetch is replaced by bounded findPendingLimit(int)")
    void unboundedFetchReplacedByBoundedLimit() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of());

        service.syncWithCentral();

        verify(outboxEventRepository, times(1)).findPendingLimit(anyInt());
        verify(outboxEventRepository, never()).findPending();
    }

    @Test
    @DisplayName("batch success still uses atomic markAsSentBatch (BugL05 contract preserved)")
    void batchSuccessStillUsesAtomicMarkAsSentBatch() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of(event("e-1"), event("e-2")));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(true);

        service.syncWithCentral();

        verify(outboxEventRepository, times(1)).markAsSentBatch(List.of("e-1", "e-2"));
        verify(outboxEventRepository, never()).markAsSent(any());
    }

    @Test
    @DisplayName("per-event failure path does not call atomic batch markAsSent")
    void perEventFailureDoesNotCallBatchMarkAsSent() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of(event("e-1"), event("e-2")));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        // batch fails, then e-1 per-event ok, e-2 per-event fails (poison).
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class)))
                .thenReturn(false)   // batch
                .thenReturn(true)    // e-1
                .thenReturn(false);  // e-2

        service.syncWithCentral();

        verify(outboxEventRepository, never()).markAsSentBatch(any());
        verify(outboxEventRepository).markAsSent("e-1");
        verify(outboxEventRepository).incrementRetry("e-2");
    }
}