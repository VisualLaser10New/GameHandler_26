package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameSessionId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;
import java.util.Optional;

public interface GameSessionRepository {
    GameSession save(GameSession session);
    Optional<GameSession> findById(GameSessionId id);
    List<GameSession> findByBuildingId(BuildingId buildingId);
    List<GameSession> findByGameType(GameType gameType);
    List<GameSession> findByStatus(GameStatus status);
    List<GameSession> findPendingSync();
    Optional<GameSession> findActiveByGameId(GameId gameId);

    /**
     * FASE 3 — all sessions in which the given user participated (any status),
     * used by {@code StatisticsService.getPlayerStatistics} to compute the
     * player's local statistics on-demand.
     */
    List<GameSession> findByParticipant(UserId userId);
}
