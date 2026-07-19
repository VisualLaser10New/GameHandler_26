package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerStatisticsDto;

import java.util.List;

/**
 * Use case per la lettura delle statistiche personali di un giocatore.
 * Le statistiche vengono calcolate su richiesta a partire dalle tabelle
 * locali delle sessioni di gioco e dei partecipanti. Un utente che non
 * ha disputato partite riceve una lista vuota, non un'eccezione.
 *
 * @see com.gameplatform.shared.dto.PlayerStatisticsDto
 */
public interface GetPlayerStatisticsUseCase {
    /**
     * Restituisce le statistiche di gioco per l'utente specificato.
     *
     * @param userId identificativo dell'utente di cui calcolare le statistiche
     * @return lista dei DTO con le statistiche del giocatore
     */
    List<PlayerStatisticsDto> getPlayerStatistics(UserId userId);
}