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
 * Receives {@code TOURNAMENT_STANDINGS_UPSERTED} events replicated from the
 * Central via outbox and applies them idempotently to the
 * {@code tournament_standings_local} table. The event carries a full
 * per-tournament standings snapshot; the sync service replaces the local
 * projection atomically (delete by {@code tournamentId} + insert of every
 * entry of the snapshot) so that re-delivery of the same event yields the
 * same end state. When {@code originatingRequestId != null} (the Central
 * return-event closes a Local admin request), the matching
 * {@code admin_requests_local} row is transitioned to {@code COMPLETED}
 * via {@link AdminRequestRepository#markCompleted}.
 */
@Service
@Transactional
public class TournamentStandingsLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentStandingsLocalSyncService.class);

    static final String EVENT_TOURNAMENT_STANDINGS_UPSERTED = "TOURNAMENT_STANDINGS_UPSERTED";

    private final TournamentStandingsLocalRepository tournamentStandingsLocalRepository;
    private final AdminRequestRepository adminRequestRepository;
    private final Clock clock;

    public TournamentStandingsLocalSyncService(TournamentStandingsLocalRepository tournamentStandingsLocalRepository,
                                                AdminRequestRepository adminRequestRepository,
                                                Clock clock) {
        this.tournamentStandingsLocalRepository = tournamentStandingsLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
        this.clock = clock;
    }

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

    private void markCompletedIfRequested(TournamentStandingsEventDto event) {
        String originatingRequestId = event.originatingRequestId();
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        String resultData = "{\"applied\":true,\"entries\":"
                + (event.entries() != null ? event.entries().size() : 0) + "}";
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