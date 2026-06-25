package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncSchedulerService {

    private final OutboxEventRepository outboxEventRepository;
    private final SyncCentralSystemPort syncCentralSystemPort;
    private final String buildingId;

    public SyncSchedulerService(
            OutboxEventRepository outboxEventRepository,
            SyncCentralSystemPort syncCentralSystemPort,
            @Value("${app.building-id}") String buildingId) {
        this.outboxEventRepository = outboxEventRepository;
        this.syncCentralSystemPort = syncCentralSystemPort;
        this.buildingId = buildingId;
    }

    @Scheduled(fixedRate = 300000)
    public void syncWithCentral() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPending();
        if (pendingEvents.isEmpty()) {
            return;
        }

        // Verify connectivity
        if (!syncCentralSystemPort.isReachable()) {
            return;
        }

        List<OutboxEventDto> dtos = pendingEvents.stream()
                .map(event -> new OutboxEventDto(
                        event.getId(),
                        event.getEventType(),
                        event.getPayload(),
                        event.getCreatedAt()
                ))
                .toList();

        SyncPayloadDto payload = new SyncPayloadDto(buildingId, dtos);

        boolean success = syncCentralSystemPort.sendSyncPayload(payload);

        if (success) {
            for (OutboxEvent event : pendingEvents) {
                outboxEventRepository.markAsSent(event.getId());
            }
        } else {
            for (OutboxEvent event : pendingEvents) {
                outboxEventRepository.incrementRetry(event.getId());
            }
        }
    }
}
