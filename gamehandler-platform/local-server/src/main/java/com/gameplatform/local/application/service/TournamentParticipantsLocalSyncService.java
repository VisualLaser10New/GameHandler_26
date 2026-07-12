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
 * Receives {@code TOURNAMENT_PARTICIPANTS_UPSERTED} events replicated from
 * the Central via outbox and applies them idempotently to the
 * {@code tournament_participants_local} table. The event carries a full
 * per-tournament participant snapshot; the sync service replaces the local
 * projection atomically (delete by {@code tournamentId} + insert of every
 * participant of the snapshot) so that re-delivery of the same event yields
 * the same end state. When {@code originatingRequestId != null} (the
 * Central return-event closes a Local admin/PLAYER request — W6 for
 * {@code PARTICIPANT_REGISTER_REQUESTED}), the matching
 * {@code admin_requests_local} row is transitioned to {@code COMPLETED}
 * via {@link AdminRequestRepository#markCompleted}.
 */
@Service
@Transactional
public class TournamentParticipantsLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentParticipantsLocalSyncService.class);

    static final String EVENT_TOURNAMENT_PARTICIPANTS_UPSERTED = "TOURNAMENT_PARTICIPANTS_UPSERTED";

    private final TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    public TournamentParticipantsLocalSyncService(TournamentParticipantsLocalRepository tournamentParticipantsLocalRepository,
                                                    AdminRequestRepository adminRequestRepository,
                                                    Clock clock) {
        this.tournamentParticipantsLocalRepository = tournamentParticipantsLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

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

    private void markCompletedIfRequested(TournamentParticipantsEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        String resultData = "{\"applied\":true,\"participants\":"
                + (event.participants() != null ? event.participants().size() : 0) + "}";
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