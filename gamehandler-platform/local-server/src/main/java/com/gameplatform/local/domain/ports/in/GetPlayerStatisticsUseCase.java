package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;

import java.util.List;

/**
 * Read use case for a player's personal statistics computed on-demand from the
 * Local {@code game_sessions}+{@code session_participants} tables (FASE 3,
 * PIANO &sect;2.5). The Local side is an offline-capable replica of the Central
 * read-model: no extra sync is required, the figures are recomputed at request
 * time. A user who has played no matches yields an <em>empty</em> list (not an
 * exception).
 */
public interface GetPlayerStatisticsUseCase {
    List<PlayerStatisticsDto> getPlayerStatistics(UserId userId);
}