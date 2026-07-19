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

/**
 * Servizio per la consultazione delle statistiche di gioco locali.
 * Implementa i casi d'uso di statistiche globali per tipo di gioco,
 * statistiche per building, sessioni attive e statistiche per giocatore.
 * Le statistiche per giocatore (FASE 3) considerano solo le sessioni
 * COMPLETATE, allineandosi con il read-model central
 * {@code player_match_facts}.
 *
 * @see GetStatisticsUseCase
 * @see GetBuildingStatisticsUseCase
 * @see GetPlayerStatisticsUseCase
 * @see ListBuildingActiveSessionsUseCase
 * @see GameSessionRepository
 * @see GameRepository
 * @see ReservationRepository
 */
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

    /**
     * Calcola le statistiche globali per un tipo di gioco, includendo
     * il conteggio delle prenotazioni e la ricostruzione delle
     * statistiche dalle sessioni completate.
     *
     * @param gameType il tipo di gioco per cui calcolare le statistiche
     * @return le statistiche calcolate
     */
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

    /**
     * Restituisce tutte le sessioni di gioco attive (stato IN_PROGRESS).
     *
     * @return la lista delle sessioni attive
     */
    @Override
    public List<GameSession> getActiveSessions() {
        return gameSessionRepository.findByStatus(GameStatus.IN_PROGRESS);
    }

    /**
     * Restituisce le sessioni di gioco attive per un determinato building.
     *
     * @param buildingId l'identificativo del building
     * @return la lista delle sessioni attive nel building
     */
    @Override
    public List<GameSession> getActiveSessionsByBuilding(BuildingId buildingId) {
        return gameSessionRepository.findByBuildingId(buildingId).stream()
                .filter(s -> s.getStatus() == GameStatus.IN_PROGRESS)
                .toList();
    }

    /**
     * Calcola le statistiche per un tipo di gioco limitate a un
     * building specifico.
     *
     * @param gameType   il tipo di gioco
     * @param buildingId l'identificativo del building
     * @return le statistiche calcolate per il building
     */
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
     * Calcola le statistiche on-demand per un giocatore dalle sessioni
     * COMPLETATE locali. Vengono conteggiate solo le sessioni completate
     * naturalmente (corrispondenti al read-model central
     * {@code player_match_facts}). Un utente senza partite restituisce
     * una lista vuota.
     *
     * @param userId l'identificativo dell'utente (non null)
     * @return la lista delle statistiche per tipo di gioco
     * @throws IllegalArgumentException se userId e' null
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

    /**
     * Aggregatore interno per le statistiche per tipo di gioco.
     * Mantiene il conteggio delle partite giocate, di quelle vinte
     * e il timestamp dell'ultima partita per un dato {@link GameType}.
     */
    private static final class PerGameType {
        int matchesPlayed;
        int matchesWon;
        Instant lastPlayedAt;
    }
}