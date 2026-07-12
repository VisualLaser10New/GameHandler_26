package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.GameDefinitionLocal;
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
 */
@Service
@Transactional
public class GameDefinitionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GameDefinitionSyncService.class);

    static final String EVENT_GAME_DEFINITION_UPSERTED = "GAME_DEFINITION_UPSERTED";

    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;
    private final Clock clock;

    public GameDefinitionSyncService(GameDefinitionLocalRepository gameDefinitionLocalRepository,
                                     Clock clock) {
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
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
            } else {
                log.warn("Unknown game-definition event type: {}", eventType);
            }
        }
    }
}
