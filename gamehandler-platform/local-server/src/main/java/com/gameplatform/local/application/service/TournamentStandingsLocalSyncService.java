package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentStandingsLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentStandingsEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Riceve eventi {@code TOURNAMENT_STANDINGS_UPSERTED} replicati dal
 * Central tramite outbox e li applica idempotentemente alla tabella
 * {@code tournament_standings_local}. L'evento trasporta uno snapshot
 * completo delle classifiche per torneo; il servizio sostituisce la
 * proiezione locale atomicamente (delete by tournamentId + insert di
 * ogni entry). Quando {@code originatingRequestId != null}, la riga
 * {@code admin_requests_local} corrispondente viene transizionata a
 * COMPLETED via {@link AdminRequestRepository#markCompleted}.
 *
 * @see TournamentStandingsLocalRepository
 * @see AdminRequestRepository
 */
@Service
@Transactional
public class TournamentStandingsLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentStandingsLocalSyncService.class);

    static final String EVENT_TOURNAMENT_STANDINGS_UPSERTED = "TOURNAMENT_STANDINGS_UPSERTED";

    private final TournamentStandingsLocalRepository tournamentStandingsLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    /**
     * Costruisce il servizio con i repository necessari per la replica
     * delle classifiche torneo e la chiusura delle richieste admin.
     *
     * @param tournamentStandingsLocalRepository il repository locale delle classifiche torneo
     * @param adminRequestRepository             il repository per la chiusura delle richieste admin
     * @param clock                              l'orologio per la generazione dei timestamp
     */
    public TournamentStandingsLocalSyncService(TournamentStandingsLocalRepository tournamentStandingsLocalRepository,
                                                AdminRequestRepository adminRequestRepository,
                                                Clock clock) {
        this.tournamentStandingsLocalRepository = tournamentStandingsLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    /**
     * Applica una lista di eventi di classifiche torneo alla tabella
     * locale. Per ogni evento TOURNAMENT_STANDINGS_UPSERTED, sostituisce
     * atomicamente la proiezione locale (delete + insert). Se l'evento
     * trasporta un originatingRequestId, la richiesta admin corrispondente
     * viene marcata come COMPLETED.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
    public void applyEvents(List<TournamentStandingsEventDto> events) {
        if (events == null) {
            return;
        }
        for (TournamentStandingsEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TOURNAMENT_STANDINGS_UPSERTED.equals(eventType)) {
                log.warn("Unknown tournament-standings event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Tournament-standings event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            // Replace the local snapshot atomically (full-snapshot idempotency).
            tournamentStandingsLocalRepository.deleteByTournament(tournamentId);
            int inserted = 0;
            if (event.entries() != null) {
                Instant updatedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now(clock);
                for (com.gameplatform.shared.dto.TournamentStandingDto entry : event.entries()) {
                    if (entry == null) {
                        continue;
                    }
                    TournamentStandingLocal standing = new TournamentStandingLocal(
                            tournamentId,
                            entry.participantId(),
                            entry.displayName(),
                            entry.wins(),
                            entry.losses(),
                            entry.points(),
                            entry.rank(),
                            updatedAt
                    );
                    tournamentStandingsLocalRepository.save(standing);
                    inserted++;
                }
            }
            log.info("Tournament-standings event [{}] replaced projection for tournament {} ({} entries)",
                    event.eventId(), tournamentId.value(), inserted);
            markCompletedIfRequested(event);
        }
    }

    /**
     * Se l'evento trasporta un {@code originatingRequestId} non blank,
     * marca la corrispondente richiesta admin come COMPLETED tramite
     * {@link AdminRequestRepository#markCompleted}, includendo nel
     * resultData il conteggio delle entry applicate.
     *
     * @param event l'evento DTO da cui estrarre l'originatingRequestId (non null)
     */
    private void markCompletedIfRequested(TournamentStandingsEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        String resultData = "{\"applied\":true,\"entries\":"
                + (event.entries() != null ? event.entries().size() : 0)
                + ",\"tournamentId\":\"" + event.tournamentId() + "\"}";
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, resultData, Instant.now(clock));
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by tournament-standings event {}",
                    originatingRequestId, event.eventId());
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, event.eventId());
        }
    }
}