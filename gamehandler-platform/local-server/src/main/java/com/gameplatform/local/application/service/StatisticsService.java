package com.gameplatform.local.application.service;

import com.gameplatform.local.domain.model.Game;
import com.gameplatform.local.domain.model.GameSession;
import com.gameplatform.local.domain.model.LocalStatistics;
import com.gameplatform.local.domain.ports.in.GetStatisticsUseCase;
import com.gameplatform.local.domain.ports.out.GameRepository;
import com.gameplatform.local.domain.ports.out.GameSessionRepository;
import com.gameplatform.local.domain.ports.out.ReservationRepository;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.GameStatus;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StatisticsService implements GetStatisticsUseCase {

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
}
