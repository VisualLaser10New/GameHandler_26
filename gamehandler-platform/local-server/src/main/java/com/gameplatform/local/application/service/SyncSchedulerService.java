package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.domain.ports.out.OutboxEventRepository;
import com.gameplatform.local.domain.ports.out.SyncCentralSystemPort;
import com.gameplatform.shared.dto.OutboxEventDto;
import com.gameplatform.shared.dto.SyncPayloadDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SyncSchedulerService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final SyncCentralSystemPort syncCentralSystemPort;
    private final OutboxSyncHelper outboxSyncHelper;
    private final String buildingId;
    private final int batchSize;

    public SyncSchedulerService(
            OutboxEventRepository outboxEventRepository,
            SyncCentralSystemPort syncCentralSystemPort,
            OutboxSyncHelper outboxSyncHelper,
            @Value("${app.building-id}") String buildingId,
            @Value("${app.outbox.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.syncCentralSystemPort = syncCentralSystemPort;
        this.outboxSyncHelper = outboxSyncHelper;
        this.buildingId = buildingId;
        this.batchSize = batchSize;
    }

    /**
     * Esegue la sincronizzazione con il sistema centrale. Recupera un
     * batch di eventi PENDING, tenta l'invio in batch; se fallisce,
     * ripiega sull'invio per-evento con retry individuale.
     */
    @Scheduled(fixedDelayString = "${app.sync-interval-ms:300000}")
    public void syncWithCentral() {
        List<OutboxEvent> batch = outboxEventRepository.findPendingLimit(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        if (!syncCentralSystemPort.isReachable()) {
            return;
        }

        List<OutboxEventDto> dtos = batch.stream()
                .map(e -> new OutboxEventDto(e.getId(), e.getEventType(), e.getPayload(), e.getCreatedAt()))
                .toList();
        SyncPayloadDto payload = new SyncPayloadDto(buildingId, dtos);

        boolean success = false;
        try {
            success = syncCentralSystemPort.sendSyncPayload(payload);
        } catch (Exception e) {
            log.warn("Batch send failed for {} event(s); falling back to per-event retry", batch.size(), e);
        }

        if (success) {
            outboxSyncHelper.markAsSent(batch.stream().map(OutboxEvent::getId).toList());
            return;
        }

        for (OutboxEvent event : batch) {
            try {
                SyncPayloadDto single = new SyncPayloadDto(buildingId,
                        List.of(new OutboxEventDto(event.getId(), event.getEventType(),
                                event.getPayload(), event.getCreatedAt())));
                boolean perOk = syncCentralSystemPort.sendSyncPayload(single);
                if (perOk) {
                    outboxEventRepository.markAsSent(event.getId());
                } else {
                    outboxEventRepository.incrementRetry(event.getId());
                }
            } catch (Exception e) {
                log.warn("Per-event sync failed for [{}]; incrementing retry", event.getId(), e);
                try {
                    outboxEventRepository.incrementRetry(event.getId());
                } catch (Exception dbEx) {
                    log.error("incrementRetry failed for [{}]", event.getId(), dbEx);
                }
            }
        }
    }
}
