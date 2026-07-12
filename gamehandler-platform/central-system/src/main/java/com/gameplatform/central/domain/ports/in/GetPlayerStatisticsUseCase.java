package com.gameplatform.central.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;

import java.util.List;

/**
 * Read use case for a player's personal statistics (FASE 3, PIANO &sect;2.4).
 *
 * <p>Returns the aggregated {@link PlayerStatisticsDto} list for the given
 * user. A user who has played no matches yields an <em>empty</em> list (not an
 * exception): {@code matchesPlayed == 0} is represented by the absence of
 * rows, consistent with the {@code player_statistics} table layout.</p>
 *
 * <p>When {@code gameType} is {@code null} the statistics for every game type
 * the user has played are returned; otherwise the result is filtered to that
 * single game type.</p>
 */
public interface GetPlayerStatisticsUseCase {
    List<PlayerStatisticsDto> getStatistics(UserId userId, GameType gameType);
}
