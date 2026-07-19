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
/**
 * Servizio applicativo lato lettura per le statistiche personali di un
 * giocatore (FASE 3). Implementa {@link GetPlayerStatisticsUseCase} leggendo
 * il read-model {@code player_statistics} centrale, proiettato da
 * {@code SyncEventProcessor}.
 *
 * <p>Un utente che non ha disputato alcuna partita restituisce una lista
 * <em>vuota</em> (non un'eccezione): {@code matchesPlayed == 0} è rappresentato
 * dall'assenza di righe. Quando {@code gameType} è non-null il risultato è
 * filtrato a quel singolo tipo di gioco; altrimenti restituisce tutti i tipi
 * giocati dall'utente.</p>
 *
 * @see GetPlayerStatisticsUseCase
 * @see PlayerStatisticsProjectionService
 */
@Service
@Transactional(readOnly = true)
public class PlayerStatisticsService implements GetPlayerStatisticsUseCase {

    private final PlayerStatisticsRepository repository;

    public PlayerStatisticsService(PlayerStatisticsRepository repository) {
        this.repository = repository;
    }

    /**
     * Restituisce le statistiche personali di un utente, opzionalmente filtrate
     * per tipo di gioco.
     *
     * @param userId l'identificativo dell'utente di cui leggere le statistiche
     *        (non deve essere {@code null})
     * @param gameType il tipo di gioco su cui filtrare, o {@code null} per
     *        restituire tutte le tipologie giocate
     * @return la lista delle statistiche dell'utente; lista vuota (mai
     *         {@code null}) se l'utente non ha statistiche per i criteri dati
     * @throws IllegalArgumentException se {@code userId} è {@code null}
     */
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

    /**
     * Converte una entità {@link PlayerStatistics} nel corrispondente DTO di
     * trasporto.
     *
     * @param stats la statistica persistita da convertire (non deve essere {@code null})
     * @return il DTO contenente i valori della statistica
     */
    private static PlayerStatisticsDto toDto(PlayerStatistics stats) {
        return new PlayerStatisticsDto(
                stats.getUserId().value(),
                stats.getGameType(),
                stats.getMatchesPlayed(),
                stats.getMatchesWon(),
                stats.getLastPlayedAt());
    }
}