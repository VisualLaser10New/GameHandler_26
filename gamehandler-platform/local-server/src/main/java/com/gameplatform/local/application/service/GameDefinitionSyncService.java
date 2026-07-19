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
 * Riceve eventi di metadati delle definizioni di gioco replicati dal Central
 * tramite outbox e li applica idempotentemente alla tabella
 * {@code game_definitions_local}. L'idempotenza e' garantita dalla chiave
 * primaria {@code game_type}: un evento GAME_DEFINITION_UPSERTED esegue un
 * upsert (merge JPA sulla riga esistente). Non viene mantenuta una tabella
 * {@code processed_events} — la ri-consegna dello stesso evento produce lo
 * stesso stato finale.
 *
 * <p>Quando {@code originatingRequestId != null} (evento di ritorno dal
 * Central che chiude una richiesta admin {@code GAME_DEFINITION_UPSERT_REQUESTED}),
 * la riga {@code admin_requests_local} corrispondente viene transizionata
 * a COMPLETED via {@link AdminRequestRepository#markCompleted}.</p>
 *
 * @see GameDefinitionLocalRepository
 * @see AdminRequestRepository
 */
@Service
@Transactional
public class GameDefinitionSyncService {

    private static final Logger log = LoggerFactory.getLogger(GameDefinitionSyncService.class);

    static final String EVENT_GAME_DEFINITION_UPSERTED = "GAME_DEFINITION_UPSERTED";

    private final GameDefinitionLocalRepository gameDefinitionLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    /**
     * Costruisce il servizio con i repository necessari per la replica
     * delle definizioni di gioco e la chiusura delle richieste admin.
     *
     * @param gameDefinitionLocalRepository il repository locale delle definizioni di gioco
     * @param adminRequestRepository        il repository per la chiusura delle richieste admin
     * @param clock                         l'orologio per la generazione dei timestamp
     */
    public GameDefinitionSyncService(GameDefinitionLocalRepository gameDefinitionLocalRepository,
                                      AdminRequestRepository adminRequestRepository,
                                      Clock clock) {
        this.gameDefinitionLocalRepository = gameDefinitionLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    /**
     * Applica una lista di eventi di definizione gioco alla tabella locale.
     * Ogni evento GAME_DEFINITION_UPSERTED viene upsertato per chiave primaria
     * game_type; se l'evento trasporta un {@code originatingRequestId}, la
     * corrispondente richiesta admin viene marcata come COMPLETED.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
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

    /**
     * Se l'evento trasporta un {@code originatingRequestId} non blank,
     * marca la corrispondente richiesta admin come COMPLETED tramite
     * {@link AdminRequestRepository#markCompleted}.
     *
     * @param event l'evento DTO da cui estrarre l'originatingRequestId (non null)
     */
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
