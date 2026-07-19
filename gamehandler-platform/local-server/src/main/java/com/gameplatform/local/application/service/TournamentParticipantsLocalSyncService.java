package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentParticipantsLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentParticipantsEventDto;
import com.gameplatform.shared.dto.TournamentParticipantViewDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Riceve eventi {@code TOURNAMENT_PARTICIPANTS_UPSERTED} replicati dal
 * Central tramite outbox e li applica idempotentemente alla tabella
 * {@code tournament_participants_local}. L'evento trasporta uno snapshot
 * completo dei partecipanti per torneo; il servizio sostituisce la
 * proiezione locale atomicamente (delete by tournamentId + insert di
 * ogni partecipante). Quando {@code originatingRequestId != null}
 * (evento di ritorno dal Central che chiude una richiesta W6), la riga
 * {@code admin_requests_local} corrispondente viene transizionata a
 * COMPLETED via {@link AdminRequestRepository#markCompleted}.
 *
 * @see TournamentParticipantsLocalRepository
 * @see AdminRequestRepository
 */
@Service
@Transactional
public class TournamentParticipantsLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentParticipantsLocalSyncService.class);

    static final String EVENT_TOURNAMENT_PARTICIPANTS_UPSERTED = "TOURNAMENT_PARTICIPANTS_UPSERTED";

    private final TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    /**
     * Costruisce il servizio con i repository necessari per la replica
     * dei partecipanti torneo e la chiusura delle richieste admin.
     *
     * @param tournamentParticipantsLocalRepository il repository locale dei partecipanti torneo
     * @param adminRequestRepository                il repository per la chiusura delle richieste admin
     * @param clock                                 l'orologio per la generazione dei timestamp
     */
    public TournamentParticipantsLocalSyncService(TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository,
                                                    AdminRequestRepository adminRequestRepository,
                                                    Clock clock) {
        this.tournamentParticipantsLocalRepository = tournamentParticipantsLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

    /**
     * Applica una lista di eventi di partecipanti torneo alla tabella
     * locale. Per ogni evento TOURNAMENT_PARTICIPANTS_UPSERTED, sostituisce
     * atomicamente la proiezione locale (delete + insert). Se l'evento
     * trasporta un originatingRequestId, la richiesta admin corrispondente
     * viene marcata come COMPLETED.
     *
     * @param events la lista di eventi da applicare (puo' essere null)
     */
    public void applyEvents(List<TournamentParticipantsEventDto> events) {
        if (events == null) {
            return;
        }
        for (TournamentParticipantsEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TOURNAMENT_PARTICIPANTS_UPSERTED.equals(eventType)) {
                log.warn("Unknown tournament-participants event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Tournament-participants event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            // Replace the local snapshot atomically (full-snapshot idempotency).
            tournamentParticipantsLocalRepository.deleteByTournament(tournamentId);
            int inserted = 0;
            if (event.participants() != null) {
                Instant updatedAt = event.updatedAt() != null ? event.updatedAt() : Instant.now(clock);
                for (TournamentParticipantViewDto entry : event.participants()) {
                    if (entry == null) {
                        continue;
                    }
                    TournamentParticipantLocal participant = new TournamentParticipantLocal(
                            tournamentId,
                            entry.participantId(),
                            entry.isTeam(),
                            entry.displayName(),
                            entry.registeredAt() != null ? entry.registeredAt() : updatedAt,
                            updatedAt
                    );
                    tournamentParticipantsLocalRepository.save(participant);
                    inserted++;
                }
            }
            log.info("Tournament-participants event [{}] replaced projection for tournament {} ({} participants)",
                    event.eventId(), tournamentId.value(), inserted);
            markCompletedIfRequested(event);
        }
    }

    /**
     * Se l'evento trasporta un {@code originatingRequestId} non blank,
     * marca la corrispondente richiesta admin come COMPLETED tramite
     * {@link AdminRequestRepository#markCompleted}, includendo nel
     * resultData il conteggio dei partecipanti applicati.
     *
     * @param event l'evento DTO da cui estrarre l'originatingRequestId (non null)
     */
    private void markCompletedIfRequested(TournamentParticipantsEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        String resultData = "{\"applied\":true,\"participants\":"
                + (event.participants() != null ? event.participants().size() : 0)
                + ",\"tournamentId\":\"" + event.tournamentId() + "\"}";
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, resultData, Instant.now(clock));
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by tournament-participants event {}",
                    originatingRequestId, event.eventId());
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, event.eventId());
        }
    }
}