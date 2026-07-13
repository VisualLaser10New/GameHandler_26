package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.ports.out.TeamMembersLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.dto.TeamMemberEntryDto;
import com.gameplatform.shared.dto.TeamMembersEventDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Receives {@code TEAM_MEMBERS_UPSERTED} events replicated from the Central
 * via outbox (BUG-TEAM-3) and applies them idempotently to the
 * {@code team_members_local} table. The event carries a full per-tournament
 * team→user membership snapshot; the sync service replaces the local
 * projection atomically (delete by {@code tournamentId} + insert of every
 * team→user entry of the snapshot) so that re-delivery of the same event
 * yields the same end state. The fresh {@code team_members_local} rows back
 * the {@code myMatches} JPQL EXISTS subquery on
 * {@code TournamentMatchLocalJpaRepository.findByParticipantAndStatus}, so
 * the PLAYER sees the match of any team they belong to.
 *
 * <p>Structural twin of {@code TournamentParticipantsLocalSyncService} minus
 * the {@code markCompletedIfRequested} hook: the admin-request closure for
 * the registration use case is driven by the parallel
 * {@code TOURNAMENT_PARTICIPANTS_UPSERTED} return event, so this event never
 * drives an {@code admin_requests_local} state transition.</p>
 */
@Service
@Transactional
public class TeamMembersLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeamMembersLocalSyncService.class);

    static final String EVENT_TEAM_MEMBERS_UPSERTED = "TEAM_MEMBERS_UPSERTED";

    private final TeamMembersLocalRepository teamMembersLocalRepository;

    public TeamMembersLocalSyncService(TeamMembersLocalRepository teamMembersLocalRepository) {
        this.teamMembersLocalRepository = teamMembersLocalRepository;
    }

    public void applyEvents(List<TeamMembersEventDto> events) {
        if (events == null) {
            return;
        }
        for (TeamMembersEventDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TEAM_MEMBERS_UPSERTED.equals(eventType)) {
                log.warn("Unknown team-members event type: {}", eventType);
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Team-members event with blank tournamentId skipped");
                continue;
            }
            TournamentId tournamentId = new TournamentId(event.tournamentId());
            // Replace the local snapshot atomically (full-snapshot idempotency).
            teamMembersLocalRepository.deleteByTournament(tournamentId);
            int inserted = 0;
            if (event.teams() != null) {
                for (TeamMemberEntryDto entry : event.teams()) {
                    if (entry == null || entry.teamId() == null || entry.teamId().isBlank()) {
                        continue;
                    }
                    if (entry.teamMembers() != null) {
                        for (String userId : entry.teamMembers()) {
                            if (userId == null || userId.isBlank()) {
                                continue;
                            }
                            teamMembersLocalRepository.save(event.tournamentId(), entry.teamId(), userId);
                            inserted++;
                        }
                    }
                }
            }
            log.info("Team-members event [{}] replaced projection for tournament {} ({} memberships)",
                    event.eventId(), tournamentId.value(), inserted);
        }
    }
}