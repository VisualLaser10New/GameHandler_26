package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import com.gameplatform.shared.dto.TournamentMatchScheduledDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Receives {@code TOURNAMENT_MATCH_SCHEDULED} events replicated from the
 * Central via outbox and applies them idempotently to the
 * {@code tournament_matches_local} table. Mirror of
 * {@link GameDefinitionSyncService}; idempotency is by PK {@code matchId}.
 */
@Service
@Transactional
public class TournamentMatchLocalSyncService {

    private static final Logger log = LoggerFactory.getLogger(TournamentMatchLocalSyncService.class);

    static final String EVENT_TOURNAMENT_MATCH_SCHEDULED = "TOURNAMENT_MATCH_SCHEDULED";

    private final TournamentMatchLocalRepository tournamentMatchLocalRepository;
    private final Clock clock;

    public TournamentMatchLocalSyncService(TournamentMatchLocalRepository tournamentMatchLocalRepository,
                                           Clock clock) {
        this.tournamentMatchLocalRepository = tournamentMatchLocalRepository;
        this.clock = clock;
    }

    public void applyEvents(List<TournamentMatchScheduledDto> events) {
        if (events == null) {
            return;
        }
        for (TournamentMatchScheduledDto event : events) {
            if (event == null) {
                continue;
            }
            String eventType = event.eventType();
            if (!EVENT_TOURNAMENT_MATCH_SCHEDULED.equals(eventType)) {
                log.warn("Unknown tournament-match event type: {}", eventType);
                continue;
            }
            if (event.matchId() == null || event.matchId().isBlank()) {
                log.warn("Tournament-match event with blank matchId skipped");
                continue;
            }
            if (event.tournamentId() == null || event.tournamentId().isBlank()) {
                log.warn("Tournament-match event {} with blank tournamentId skipped", event.matchId());
                continue;
            }
            if (event.gameType() == null) {
                log.warn("Tournament-match event {} with null gameType skipped", event.matchId());
                continue;
            }
            if (event.participantA() == null || event.participantA().isBlank()) {
                log.warn("Tournament-match event {} with blank participantA skipped", event.matchId());
                continue;
            }
            TournamentMatchStatus status;
            try {
                status = event.status() != null && !event.status().isBlank()
                        ? TournamentMatchStatus.valueOf(event.status())
                        : TournamentMatchStatus.SCHEDULED;
            } catch (IllegalArgumentException e) {
                log.warn("Unknown TournamentMatchStatus '{}' on event {}, defaulting to SCHEDULED",
                        event.status(), event.matchId());
                status = TournamentMatchStatus.SCHEDULED;
            }
            TournamentMatchLocal match = new TournamentMatchLocal(
                    new TournamentMatchId(event.matchId()),
                    new TournamentId(event.tournamentId()),
                    event.round(),
                    event.bracketPosition(),
                    event.participantA(),
                    event.participantB(),
                    event.gameType(),
                    event.gameId(),
                    status,
                    event.scheduledAt() != null ? event.scheduledAt() : Instant.now(clock)
            );
            tournamentMatchLocalRepository.save(match);
        }
    }
}