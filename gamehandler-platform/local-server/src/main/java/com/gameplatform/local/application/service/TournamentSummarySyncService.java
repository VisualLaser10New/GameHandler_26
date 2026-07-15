package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.domain.ports.out.AdminRequestRepository;
import com.gameplatform.local.domain.ports.out.TournamentSummaryLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TournamentSummaryEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Receives {@code TOURNAMENT_SUMMARY_UPSERTED} events replicated from the
 * Central via outbox and applies them idempotently to the
 * {@code tournaments_summary_local} table. Mirror of
 * {@link TournamentMatchLocalSyncService} and {@code GameDefinitionSyncService};
 * idempotency is by PK {@code tournamentId} (upsert on the local repository's
 * {@code save}, physical {@code deleteById} for tombstones).
 *
 * <p>For each event in the batch:
 * <ul>
 *   <li>{@code deleted == true} (tombstone) → the projection row is physically
 *       removed via {@code tournamentSummaryLocalRepository.deleteById}. Safe
 *       on re-delivery: {@code deleteById} on a missing PK is a no-op.</li>
 *   <li>otherwise → a fresh {@link TournamentSummaryLocal} snapshot is built
 *       from the DTO and {@code save}-d (upsert by PK).</li>
 * </ul>
 *
 * <p>When {@code originatingRequestId != null} (the Central return-event
 * closes an admin/PLAYER W use case from the Local §7.B), the matching
 * {@code admin_requests_local} row is transitioned to {@code COMPLETED} via
 * {@link AdminRequestRepository#markCompleted}. The transition is a
 * conditional {@code WHERE status = 'PENDING'} UPDATE — idempotent on
 * re-delivery of the same return-event (a second call against an
 * already-COMPLETED row is a no-op). The {2} return value is logged at
 * DEBUG.</p>
 */
@Service
@Transactional
public class TournamentSummarySyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentSummarySyncService.class);

    static final String EVENT_TOURNAMENT_SUMMARY_UPSERTED = "TOURNAMENT_SUMMARY_UPSERTED";

    private final TournamentSummaryLocalRepository tournamentSummaryLocalRepository;
    private final AdminRequestRepository adminRequestRepository;

    public TournamentSummarySyncService(TournamentSummaryLocalRepository tournamentSummaryLocalRepository,
                                         AdminRequestRepository adminRequestRepository) {
        this.tournamentSummaryLocalRepository = tournamentSummaryLocalRepository;
        this.adminRequestRepository = adminRequestRepository;
    }

    public void applyEvents(List<TournamentSummaryEventDto> events) {
        if (events == null) {
            return;
        }
        for (TournamentSummaryEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TOURNAMENT_SUMMARY_UPSERTED.equals(eventType)) {
                log.warn("Unknown tournament-summary event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Tournament-summary event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            boolean tombstone = event.deleted();
            if (tombstone) {
                log.info("Tournament-summary tombstone event [{}] for tournament {} — deleting projection row",
                        event.eventId(), tournamentId.value());
                tournamentSummaryLocalRepository.deleteById(tournamentId);
            } else {
                TournamentSummaryLocal summary = new TournamentSummaryLocal(
                        tournamentId,
                        event.name(),
                        event.gameType(),
                        event.teamBased(),
                        event.teamSize(),
                        event.status(),
                        event.startsAt(),
                        event.endsAt(),
                        event.buildingIds(),
                        event.participantsCount(),
                        false,
                        event.updatedAt() != null ? event.updatedAt() : java.time.Instant.now()
                );
                tournamentSummaryLocalRepository.save(summary);
                log.info("Tournament-summary event [{}] upserted for tournament {} (status={}, participants={})",
                        event.eventId(), tournamentId.value(), summary.getStatus(), summary.getParticipantsCount());
            }
            markCompletedIfRequested(event.eventId(), event.originatingRequestId(), tombstone, event.tournamentId());
        }
    }

    private void markCompletedIfRequested(String eventId, String originatingRequestId, boolean tombstone,
                                          String tournamentId) {
        if (originatingRequestId == null || originatingRequestId.isBlank()) {
            return;
        }
        String resultData = (tombstone ? "{\"deleted\":true" : "{\"deleted\":false,\"applied\":true")
                + ",\"tournamentId\":\"" + tournamentId + "\"}";
        int mutated = adminRequestRepository.markCompleted(
                originatingRequestId, resultData, java.time.Instant.now());
        if (mutated > 0) {
            log.info("Admin request {} marked COMPLETED by tournament-summary event {}",
                    originatingRequestId, eventId);
        } else if (log.isDebugEnabled()) {
            log.debug("Admin request {} already resolved or unknown — markCompleted returned 0 (event {})",
                    originatingRequestId, eventId);
        }
    }
}
