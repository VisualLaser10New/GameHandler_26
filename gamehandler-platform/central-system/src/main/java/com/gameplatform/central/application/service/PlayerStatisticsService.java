package com.gameplatform.central.application.service;

import com.gameplatform.central.domain.model.PlayerStatistics;
import com.gameplatform.central.domain.ports.in.GetPlayerStatisticsUseCase;
import com.gameplatform.central.domain.ports.out.PlayerStatisticsRepository;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-side application service for a player's personal statistics (FASE 3,
 * PIANO &sect;2.4). Implements {@link GetPlayerStatisticsUseCase} by reading the
 * Central {@code player_statistics} read-model, projected by the
 * {@code SyncEventProcessor}.
 *
 * <p>A user who has played no matches yields an <em>empty</em> list (not an
 * exception): {@code matchesPlayed == 0} is represented by the absence of
 * rows, consistent with the {@code player_statistics} layout (protocol &sect;2.C
 * &mdash; "Eccezioni Dogmatiche"). When {@code gameType} is non-null the result
 * is filtered to that single game type; otherwise every game type the user has
 * played is returned.</p>
 */
@Service
@Transactional(readOnly = true)
public class PlayerStatisticsService implements GetPlayerStatisticsUseCase {

    private final PlayerStatisticsRepository repository;

    public PlayerStatisticsService(PlayerStatisticsRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<PlayerStatisticsDto> getStatistics(UserId userId, GameType gameType) {
        if (userId == null) {
            throw new IllegalArgumentException("userId cannot be null");
        }
        if (gameType != null) {
            return repository.findByUserIdAndGameType(userId, gameType).stream()
                    .map(PlayerStatisticsService::toDto)
                    .toList();
        }
        return repository.findByUserId(userId).stream()
                .map(PlayerStatisticsService::toDto)
                .toList();
    }

    private static PlayerStatisticsDto toDto(PlayerStatistics stats) {
        return new PlayerStatisticsDto(
                stats.getUserId().value(),
                stats.getGameType(),
                stats.getMatchesPlayed(),
                stats.getMatchesWon(),
                stats.getLastPlayedAt());
    }
}