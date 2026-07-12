package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.RegisteredLocalServerLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.RegisteredLocalServerLocalRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.dto.LocalServerRegistryEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Receives {@code LOCAL_SERVER_REGISTRY_UPSERTED} events replicated from
 * the Central via outbox and applies them idempotently to the
 * {@code registered_local_servers_local} table. Each event carries a
 * single registered-local-server row; the sync service upserts it by PK
 * {@code buildingId}. Lets a PLATFORM_ADMIN connected to any Local see the
 * full registry of active/inactive servers without a direct Central call
 * (E1, PIANO §7.B).
 */
@Service
@Transactional
public class RegisteredLocalServerSyncService {

    private static final Logger log = LoggerFactory.getLogger(RegisteredLocalServerSyncService.class);

    static final String EVENT_LOCAL_SERVER_REGISTRY_UPSERTED = "LOCAL_SERVER_REGISTRY_UPSERTED";

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    public RegisteredLocalServerSyncService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository,
                                             AdminRequestRepository adminRequestRepository,
                                             Clock clock) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    public void applyEvents(List<LocalServerRegistryEventDto> events) {
        if (events == null) {
            return;
        }
        for (LocalServerRegistryEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_LOCAL_SERVER_REGISTRY_UPSERTED.equals(eventType)) {
                log.warn("Unknown local-server-registry event type: {}", eventType);
                continue;
            }
            if (event.buildingId() == null || event.buildingId().isBlank()) {
                log.warn("Local-server-registry event with blank buildingId skipped");
                continue;
            }
            RegisteredLocalServerLocal server = new RegisteredLocalServerLocal(
                    new BuildingId(event.buildingId()),
                    event.baseUrl(),
                    event.lastSeenAt(),
                    event.active(),
                    event.updatedAt() != null ? event.updatedAt() : Instant.now(clock)
            );
            registeredLocalServerLocalRepository.save(server);
            log.info("Local-server-registry event [{}] upserted for building {} (active={})",
                    event.eventId(), event.buildingId(), event.active());
            markCompletedIfRequested(event);
        }
    }

    private void markCompletedIfRequested(LocalServerRegistryEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, "{\"applied\":true}", Instant.now(clock));
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by local-server-registry event {}",
                    originatingRequestId, event.eventId());
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, event.eventId());
        }
    }
}