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
 * Riceve eventi {@code LOCAL_SERVER_REGISTRY_UPSERTED} replicati dal
 * Central tramite outbox e li applica idempotentemente alla tabella
 * {@code registered_local_servers_local}. Ogni evento trasporta una riga
 * di server locale registrato; il servizio esegue un upsert per chiave
 * primaria {@code buildingId}. Consente a un PLATFORM_ADMIN connesso a
 * qualsiasi Local di vedere il registro completo dei server attivi/inattivi
 * senza una chiamata diretta al Central (E1, PIANO §7.B).
 *
 * @see RegisteredLocalServerLocalRepository
 * @see AdminRequestRepository
 */
@Service
@Transactional
public class RegisteredLocalServerSyncService {

    private static final Logger log = LoggerFactory.getLogger(RegisteredLocalServerSyncService.class);

    static final String EVENT_LOCAL_SERVER_REGISTRY_UPSERTED = "LOCAL_SERVER_REGISTRY_UPSERTED";

    private final RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    /**
     * Costruisce il servizio con i repository necessari per la replica
     * del registro server locali e la chiusura delle richieste admin.
     *
     * @param registeredLocalServerLocalRepository il repository locale del registro server
     * @param adminRequestRepository               il repository per la chiusura delle richieste admin
     * @param clock                                l'orologio per la generazione dei timestamp
     */
    public RegisteredLocalServerSyncService(RegisteredLocalServerLocalRepository registeredLocalServerLocalRepository,
                                             AdminRequestRepository adminRequestRepository,
                                             Clock clock) {
        this.registeredLocalServerLocalRepository = registeredLocalServerLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    /**
     * Applica una lista di eventi di registro server locale alla tabella
     * locale. Ogni evento LOCAL_SERVER_REGISTRY_UPSERTED viene upsertato
     * per buildingId; se l'evento trasporta un originatingRequestId, la
     * corrispondente richiesta admin viene marcata come COMPLETED.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
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

    /**
     * Se l'evento trasporta un {@code originatingRequestId} non blank,
     * marca la corrispondente richiesta admin come COMPLETED tramite
     * {@link AdminRequestRepository#markCompleted}.
     *
     * @param event l'evento DTO da cui estrarre l'originatingRequestId (non null)
     */
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