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
 * Receives LOCAL_ADMIN↔building metadata events replicated from the Central via
 * outbox and applies them idempotently to the {@code local_admin_buildings_local}
 * table. Idempotency is by composite PK: an ASSIGNED event upserts (JPA merge on
 * an existing (user_id, building_id) row), a REVOKED event deletes (no-op if the
 * row is already absent). No {@code processed_events} table is kept on local —
 * re-delivery of the same event yields the same end state.
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