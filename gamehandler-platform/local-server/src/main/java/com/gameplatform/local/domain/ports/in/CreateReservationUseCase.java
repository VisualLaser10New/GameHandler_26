package com.gameplatform.local.domain.ports.in;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;

/**
 * Use case per la creazione di una nuova prenotazione per un gioco.
 * Associa un utente a un gioco specifico in una finestra temporale
 * definita, verificando la disponibilit&agrave; della fascia oraria.
 *
 * @see com.gameplatform.local.domain.model.Reservation
 */
public interface CreateReservationUseCase {
    /**
     * Crea una prenotazione per il gioco e l'utente specificati.
     *
     * @param gameId identificativo del gioco da prenotare
     * @param userId identificativo dell'utente che effettua la prenotazione
     * @param start  istante di inizio della prenotazione
     * @param end    istante di fine della prenotazione
     * @return la prenotazione creata
     */
    Reservation create(GameId gameId, UserId userId, Instant start, Instant end);
}
