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
    private final OutboxSyncHelper outboxSyncHelper;
    private final String buildingId;

    public SyncSchedulerService(
            OutboxEventRepository outboxEventRepository,
            SyncCentralSystemPort syncCentralSystemPort,
            OutboxSyncHelper outboxSyncHelper,
            @Value("${app.building-id}") String buildingId) {
        this.outboxEventRepository = outboxEventRepository;
        this.syncCentralSystemPort = syncCentralSystemPort;
        this.outboxSyncHelper = outboxSyncHelper;
        this.buildingId = buildingId;
    }

    @Scheduled(fixedDelayString = "${app.sync-interval-ms:300000}")
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

        List<String> eventIds = pendingEvents.stream()
                .map(OutboxEvent::getId)
                .toList();

        boolean success = syncCentralSystemPort.sendSyncPayload(payload);

        if (success) {
            outboxSyncHelper.markAsSent(eventIds);
        } else {
            outboxSyncHelper.incrementRetry(eventIds);
        }
    }
}
