package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.GameDefinitionLocalRepository;
import com.gameplatform.shared.dto.GameDefinitionEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Receives game-definition metadata events replicated from the Central via
 * outbox and applies them idempotently to the {@code game_definitions_local}
 * table. Idempotency is by PK {@code game_type}: a GAME_DEFINITION_UPSERTED
 * event upserts (JPA merge on an existing game_type row). No
 * {@code processed_events} table is kept on local — re-delivery of the same
 * event yields the same end state.
 *
 * <p>When {@code originatingRequestId != null} (the Central return-event
 * closes a Local-admin {@code GAME_DEFINITION_UPSERT_REQUESTED} request,
 * PIANO §7.A.7 / §7.B W9), the matching {@code admin_requests_local} row is
 * transitioned to {@code COMPLETED} via
 * {@link AdminRequestRepository#markCompleted}.</p>
 */
@Service
@Transactional
public class GameDefinitionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GameDefinitionSyncService.class);

    static final String EVENT_GAME_DEFINITION_UPSERTED = "GAME_DEFINITION_UPSERTED";

    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    public GameDefinitionSyncService(GameDefinitionLocalRepository gameDefinitionLocalRepository,
                                      AdminRequestRepository adminRequestRepository,
                                      Clock clock) {
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    public void applyEvents(List<GameDefinitionEventDto> events) {
        if (events == null) {
            return;
        }
        for (GameDefinitionEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (EVENT_GAME_DEFINITION_UPSERTED.equals(eventType)) {
                GameDefinitionLocal def = new GameDefinitionLocal(
                        event.gameType(),
                        event.name(),
                        event.minPlayers(),
                        event.maxPlayers(),
                        event.teamAllowed(),
                        event.registrationRules(),
                        event.updatedAt() != null ? event.updatedAt() : Instant.now(clock)
                );
                gameDefinitionLocalRepository.save(def);
                markCompletedIfRequested(event);
            } else {
                log.warn("Unknown game-definition event type: {}", eventType);
            }
        }
    }

    private void markCompletedIfRequested(GameDefinitionEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, "{\"applied\":true}", Instant.now(clock));
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by game-definition event [{}]",
                    originatingRequestId, event.eventId());
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, event.eventId());
        }
    }
}
