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

    /**
     * Restituisce le statistiche personali aggregate per l'utente indicato.
     *
     * @param userId l'identificativo dell'utente di cui recuperare le statistiche; non deve essere {@code null}
     * @param gameType il tipo di gioco su cui filtrare le statistiche; se {@code null} sono incluse tutte le tipologie giocate dall'utente
     * @return la lista di {@link PlayerStatisticsDto} contenente le statistiche aggregate; la lista è vuota se l'utente non ha disputato alcun incontro
     * @throws com.gameplatform.shared.domain.exception.UserNotFoundException se l'utente indicato non esiste
     */
    List<PlayerStatisticsDto> getStatistics(UserId userId, GameType gameType);
}
