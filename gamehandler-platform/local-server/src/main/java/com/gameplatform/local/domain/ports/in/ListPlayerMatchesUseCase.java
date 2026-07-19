package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.UserId;
import com.gameplatform.shared.dto.PlayerMatchDto;

import java.util.List;

/**
 * Use case per la lettura delle partite completate di un giocatore.
 * Restituisce le sessioni di gioco completate in cui l'utente specificato
 * ha partecipato, opzionalmente filtrate per tipo di gioco.
 *
 * @see com.gameplatform.shared.dto.PlayerMatchDto
 */
public interface ListPlayerMatchesUseCase {

    /**
     * Restituisce le partite completate dell'utente specificato, con filtro opzionale per tipo di gioco.
     *
     * @param userId         identificativo dell'utente
     * @param gameTypeFilter tipo di gioco per filtrare i risultati, oppure null per nessun filtro
     * @return lista dei DTO delle partite completate
     */
    List<PlayerMatchDto> listCompletedMatches(UserId userId, GameType gameTypeFilter);
}