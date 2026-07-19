package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.UserId;
import java.util.List;

/**
 * Use case per la lettura delle prenotazioni. Fornisce due modalit&agrave;
 * di consultazione: per utente o per gioco, restituendo l'elenco delle
 * prenotazioni corrispondenti.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 */
public interface GetReservationsUseCase {
    /**
     * Restituisce le prenotazioni effettuate dall'utente specificato.
     *
     * @param userId identificativo dell'utente
     * @return lista delle prenotazioni dell'utente
     */
    List<Reservation> getByUser(UserId userId);

    /**
     * Restituisce le prenotazioni per il gioco specificato.
     *
     * @param gameId identificativo del gioco
     * @return lista delle prenotazioni per il gioco
     */
    List<Reservation> getByGame(GameId gameId);
}
