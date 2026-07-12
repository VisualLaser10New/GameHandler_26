package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.PlayerMatchFact;
import com.gameplatform.central.domain.ports.out.PlayerMatchFactRepository;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.domain.model.WinCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Write-side projection service that consumes an enriched
 * {@code GAME_SESSION_COMPLETED} event and populates the FASE 3 player
 * read-models (PIANO &sect;2.4 / &sect;2.2).
 *
 * <p>For each participant it writes one {@link PlayerMatchFact} (idempotent via
 * the composite PK {@code (session_id, user_id)}) and, only when the fact is
 * newly inserted, atomically increments the matching {@code PlayerStatistics}
 * row. This conditional increment guarantees full idempotency: a reprocessed
 * event finds every fact already present, so {@code saveIfAbsent} returns
 * {@code false} and the counters are not double-counted.</p>
 *
 * <p><strong>Transactionality:</strong> this service is intentionally NOT
 * annotated {@code @Transactional}. It is always invoked from
 * {@code SyncEventProcessor#processOne} (a {@code REQUIRES_NEW} transaction);
 * the adapter-level writes ({@code saveIfAbsent}, {@code increment}, and the
 * pessimistic-write lock they rely on) all participate in that outer
 * transaction, so the fact inserts and the counter increments commit
 * atomically (protocol &sect;2.C &mdash; "Atomicit&agrave;"). Invoking this service
 * outside a transaction is a programming error (the {@code PESSIMISTIC_WRITE}
 * lock and the EntityManager flushes require an active transaction).</p>
 */
@Service
public class PlayerStatisticsProjectionService {

    private static final Logger log = LoggerFactory.getLogger(PlayerStatisticsProjectionService.class);

    private final PlayerMatchFactRepository playerMatchFactRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;

    public PlayerStatisticsProjectionService(PlayerMatchFactRepository playerMatchFactRepository,
                                             PlayerStatisticsRepository playerStatisticsRepository) {
        this.playerMatchFactRepository = playerMatchFactRepository;
        this.playerStatisticsRepository = playerStatisticsRepository;
    }

    /**
     * Project one completed game session into the player read-models.
     *
     * @param buildingId   the building where the session was played
     * @param gameType     the game type of the session
     * @param sessionId    the session identifier (fact PK component)
     * @param participants the participating user ids (raw JWT user-id strings)
     * @param winnerId     the winning user id, or {@code null} for a draw
     * @param winCondition the session's win condition, or {@code null}
     * @param endedAt      when the session ended (fact {@code ended_at})
     */
    public void onGameSessionCompleted(BuildingId buildingId,
                                       GameType gameType,
                                       String sessionId,
                                       List<String> participants,
                                       String winnerId,
                                       WinCondition winCondition,
                                       Instant endedAt) {
        if (buildingId == null || gameType == null || sessionId == null || endedAt == null) {
            log.warn("PlayerStatisticsProjection: skipping projection with null core fields (buildingId={}, gameType={}, sessionId={}, endedAt={})",
                    buildingId, gameType, sessionId, endedAt);
            return;
        }
        if (participants == null || participants.isEmpty()) {
            log.debug("PlayerStatisticsProjection: event [{}] carries no participants, skipping player read-model update", sessionId);
            return;
        }
        for (String participantUid : participants) {
            if (participantUid == null || participantUid.isBlank()) {
                continue;
            }
            if (participantUid.length() > 36) {
                // Defensive: the player_match_facts.user_id column is VARCHAR(36); a
                // malformed payload must not abort the aggregated-stats transaction.
                log.warn("PlayerStatisticsProjection: skipping participant uid longer than 36 chars in event [{}]", sessionId);
                continue;
            }
            UserId userId = new UserId(participantUid);
            boolean won = (winnerId != null && winnerId.equals(participantUid));
            PlayerMatchFact fact = new PlayerMatchFact(
                    sessionId, userId, buildingId, gameType, null, won, winCondition, endedAt);
            boolean newlyInserted = playerMatchFactRepository.saveIfAbsent(fact);
            if (newlyInserted) {
                playerStatisticsRepository.increment(userId, gameType, won, endedAt);
            }
        }
    }
}