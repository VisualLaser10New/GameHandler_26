package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.SyncPayloadDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug L-05: SyncSchedulerService is NOT annotated with {@code @Transactional}.
 *
 * <p>After a successful sync, events are marked as SENT one by one in a loop (lines 55-58).
 * Without {@code @Transactional}, if {@code markAsSent()} fails mid-loop, some events
 * are marked as SENT and others remain PENDING — a non-atomic partial update.</p>
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
    @DisplayName("BUG L-05: markAsSent fails on 3rd event — first 2 are marked SENT, 3rd remains PENDING (non-atomic without @Transactional)")
    void partialMarkAsSentWhenThirdCallFails() {
        // -- Create 4 pending outbox events
        OutboxEvent event1 = new OutboxEvent("evt-1", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event2 = new OutboxEvent("evt-2", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event3 = new OutboxEvent("evt-3", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);
        OutboxEvent event4 = new OutboxEvent("evt-4", "GAME_SESSION_COMPLETED", "{}", "PENDING", NOW, null, 0);

        when(outboxEventRepository.findPending()).thenReturn(List.of(event1, event2, event3, event4));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(true);

        // -- markAsSent succeeds for events 1 and 2, but throws on event 3
        doNothing().when(outboxEventRepository).markAsSent("evt-1");
        doNothing().when(outboxEventRepository).markAsSent("evt-2");
        doThrow(new RuntimeException("Database connection lost"))
                .when(outboxEventRepository).markAsSent("evt-3");

        // -- Execute sync
        assertThrows(RuntimeException.class, () -> syncSchedulerService.syncWithCentral(),
                "syncWithCentral should propagate the exception from markAsSent");

        // BUG EXPOSED: Without @Transactional, events 1 and 2 were already marked as SENT
        // before the failure on event 3. This is a partial update — non-atomic.
        InOrder inOrder = inOrder(outboxEventRepository);
        inOrder.verify(outboxEventRepository).markAsSent("evt-1"); // marked SENT ✓
        inOrder.verify(outboxEventRepository).markAsSent("evt-2"); // marked SENT ✓
        inOrder.verify(outboxEventRepository).markAsSent("evt-3"); // FAILED — throws RuntimeException

        // Event 4 was never reached
        verify(outboxEventRepository, never()).markAsSent("evt-4");

        // Events 1 and 2 are permanently marked as SENT, but event 3 and 4 remain PENDING.
        // On the next sync cycle, only events 3 and 4 will be retried, but events 1 and 2
        // will NOT be included — they were already marked SENT even though the batch was not
        // atomically committed. With @Transactional, all 4 markAsSent calls would have
        // been rolled back.
    }
}
