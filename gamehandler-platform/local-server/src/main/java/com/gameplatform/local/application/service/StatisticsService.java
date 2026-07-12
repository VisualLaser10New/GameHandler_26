package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetBuildingStatisticsUseCase;
import com.gameplatform.local.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.local.domain.ports.in.GetStatisticsUseCase;
import com.gameplatform.local.domain.ports.in.ListBuildingActiveSessionsUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StatisticsService implements GetStatisticsUseCase, ListBuildingActiveSessionsUseCase, GetBuildingStatisticsUseCase, GetPlayerStatisticsUseCase {

    private final GameSessionRepository gameSessionRepository;
    private final GameRepository gameRepository;
    private final ReservationRepository reservationRepository;

    public StatisticsService(
            GameSessionRepository gameSessionRepository,
            GameRepository gameRepository,
            ReservationRepository reservationRepository) {
        this.gameSessionRepository = gameSessionRepository;
        this.gameRepository = gameRepository;
        this.reservationRepository = reservationRepository;
    }

    @Override
    public LocalStatistics getStatistics(GameType gameType) {
        // Compute total reservations count for all game machines of this type
        List<Game> gamesOfType = gameRepository.findAll().stream()
                .filter(game -> game.getGameType() == gameType)
                .toList();

        List<GameId> gameIds = gamesOfType.stream()
                .map(Game::getId)
                .toList();

        int totalReservations = reservationRepository.countByGameIds(gameIds);

        // Retrieve sessions and recalculate stats
        List<GameSession> sessions = gameSessionRepository.findByGameType(gameType);

        LocalStatistics stats = new LocalStatistics(gameType, 0, 0.0, 0, new HashMap<>());
        stats.recalculate(sessions);
        stats.setTotalReservations(totalReservations);

        return stats;
    }

    @Override
    public List<GameSession> getActiveSessions() {
        return gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS);
    }

    @Override
    public List<GameSession> getActiveSessionsByBuilding(BuildingId buildingId) {
        return gameSessionRepository.findByBuildingId(buildingId).stream()
                .filter(s -> s.getStatus() == GameStatus.IN_PROGRESS)
                .toList();
    }

    @Override
    public LocalStatistics getStatisticsForBuilding(GameType gameType, BuildingId buildingId) {
        List<Game> gamesOfType = gameRepository.findByBuildingId(buildingId).stream()
                .filter(game -> game.getGameType() == gameType)
                .toList();

        List<GameId> gameIds = gamesOfType.stream()
                .map(Game::getId)
                .toList();

        int totalReservations = reservationRepository.countByGameIds(gameIds);

        List<GameSession> sessions = gameSessionRepository.findByBuildingId(buildingId).stream()
                .filter(s -> s.getGameType() == gameType)
                .toList();

        LocalStatistics stats = new LocalStatistics(gameType, 0, 0.0, 0, new HashMap<>());
        stats.recalculate(sessions);
        stats.setTotalReservations(totalReservations);

        return stats;
    }

    /**
     * FASE 3 — on-demand per-player statistics derived from the local
     * {@code game_sessions}+{@code session_participants} tables (PIANO &sect;2.5).
     *
     * <p>Only sessions that reached a natural {@code COMPLETED} state are
     * counted as "played": this matches the Central {@code player_match_facts}
     * read-model, which is populated solely from {@code GAME_SESSION_COMPLETED}
     * outbox events (the Local {@code GameSessionService.end} emits those only
     * for sessions that were not already aborted). A single {@code endedAt} is
     * the latest {@code ended_at} among the player's completed sessions for the
     * game type. A user who has played no matches yields an <em>empty</em>
     * list (not an exception).</p>
     */
    @Override
    public List<PlayerStatisticsDto> getPlayerStatistics(UserId userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        List<GameSession> completed = gameSessionRepository.findByParticipant(userId).stream()
                .filter(s -> s.getStatus() == GameStatus.COMPLETED)
                .toList();

        Map<GameType, PerGameType> perGameType = new LinkedHashMap<>();
        for (GameSession session : completed) {
            PerGameType agg = perGameType.computeIfAbsent(session.getGameType(), k -> new PerGameType());
            agg.matchesPlayed++;
            if (session.getWinnerId() != null && session.getWinnerId().equals(userId)) {
                agg.matchesWon++;
            }
            Instant endedAt = session.getEndedAt();
            if (endedAt != null && (agg.lastPlayedAt == null || endedAt.isAfter(agg.lastPlayedAt))) {
                agg.lastPlayedAt = endedAt;
            }
        }

        return perGameType.entrySet().stream()
                .map(e -> new PlayerStatisticsDto(
                        userId.value(),
                        e.getKey(),
                        e.getValue().matchesPlayed,
                        e.getValue().matchesWon,
                        e.getValue().lastPlayedAt))
                .toList();
    }

    private static final class PerGameType {
        int matchesPlayed;
        int matchesWon;
        Instant lastPlayedAt;
    }
}