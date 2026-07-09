package com.gameplatform.local.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.SyncPayloadDto;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SyncSchedulerServiceTest {

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
        return new OutboxEvent(id, "RESERVATION_CREATED", "{}", "PENDING", Instant.now(), null, 0);
    }

    @Test
    void shouldDoNothingWhenNoPendingEvents() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of());
        service.syncWithCentral();
        verify(syncCentralSystemPort, never()).isReachable();
        verify(syncCentralSystemPort, never()).sendSyncPayload(any());
        verify(outboxEventRepository, never()).markAsSentBatch(any());
        verify(outboxEventRepository, never()).incrementRetryBatch(any());
    }

    @Test
    void shouldSkipWhenCentralUnreachable() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of(event("e-1")));
        when(syncCentralSystemPort.isReachable()).thenReturn(false);
        service.syncWithCentral();
        verify(syncCentralSystemPort, never()).sendSyncPayload(any());
        verify(outboxEventRepository, never()).markAsSentBatch(any());
        verify(outboxEventRepository, never()).incrementRetryBatch(any());
    }

    @Test
    void shouldMarkEventsAsSentOnSuccess() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of(event("e-1"), event("e-2")));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(true);

        service.syncWithCentral();

        verify(syncCentralSystemPort).sendSyncPayload(argThat(p -> "building-1".equals(p.buildingId()) && p.events().size() == 2));
        verify(outboxEventRepository).markAsSentBatch(List.of("e-1", "e-2"));
        verify(outboxEventRepository, never()).incrementRetryBatch(any());
    }

    @Test
    void shouldRetryPerEventWhenBatchTransportFails() {
        when(outboxEventRepository.findPendingLimit(anyInt())).thenReturn(List.of(event("e-1"), event("e-2")));
        when(syncCentralSystemPort.isReachable()).thenReturn(true);
        when(syncCentralSystemPort.sendSyncPayload(any(SyncPayloadDto.class))).thenReturn(false);

        service.syncWithCentral();

        // Batch transport failed → no atomic batch markAsSent; instead each event is retried per-event.
        verify(outboxEventRepository, never()).markAsSentBatch(any());
        verify(outboxEventRepository).incrementRetry("e-1");
        verify(outboxEventRepository).incrementRetry("e-2");
    }
}