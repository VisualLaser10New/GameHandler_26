package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.LocalAdminBuildingEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Riceve eventi di metadati LOCAL_ADMIN↔building replicati dal Central
 * tramite outbox e li applica idempotentemente alla tabella
 * {@code local_admin_buildings_local}. L'idempotenza e' garantita dalla
 * chiave composita: un evento ASSIGNED esegue un upsert (merge JPA sulla
 * riga (user_id, building_id) esistente), un evento REVOKED elimina
 * (no-op se la riga e' gia' assente). Non viene mantenuta una tabella
 * {@code processed_events} — la ri-consegna dello stesso evento produce
 * lo stesso stato finale.
 *
 * @see LocalAdminBuildingLocalRepository
 */
@Service
@Transactional
public class LocalAdminBuildingSyncService {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminBuildingSyncService.class);

    static final String EVENT_ASSIGNED = "LOCAL_ADMIN_BUILDING_ASSIGNED";
    static final String EVENT_REVOKED = "LOCAL_ADMIN_BUILDING_REVOKED";

    private final LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository;
    private final Clock clock;

    public LocalAdminBuildingSyncService(LocalAdminBuildingLocalRepository localAdminBuildingLocalRepository,
                                         Clock clock) {
        this.localAdminBuildingLocalRepository = localAdminBuildingLocalRepository;
        this.clock = clock;
    }

    /**
     * Applica una lista di eventi di associazione LOCAL_ADMIN-building
     * alla tabella locale. Ogni evento ASSIGNED viene upsertato, ogni
     * evento REVOKED viene eliminato.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
    public void applyEvents(List<LocalAdminBuildingEventDto> events) {
        if (events == null) {
            return;
        }
        for (LocalAdminBuildingEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (EVENT_ASSIGNED.equals(eventType)) {
                LocalAdminBuilding binding = new LocalAdminBuilding(
                        new UserId(event.userId()),
                        new BuildingId(event.buildingId()),
                        event.assignedAt() != null ? event.assignedAt() : Instant.now(clock)
                );
                localAdminBuildingLocalRepository.save(binding);
            } else if (EVENT_REVOKED.equals(eventType)) {
                localAdminBuildingLocalRepository.deleteByUserIdAndBuildingId(
                        new UserId(event.userId()), new BuildingId(event.buildingId()));
            } else {
                log.warn("Unknown metadata event type: {}", eventType);
            }
        }
    }
}