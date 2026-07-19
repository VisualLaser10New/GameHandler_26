package com.gameplatform.local.domain.ports.in;

import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.UserId;

/**
 * Use case per la cancellazione di una prenotazione di gioco.
 * Fornisce due modalit&agrave; di cancellazione: una senza verifica di
 * propriet&agrave; riservata ai percorsi interni o amministrativi, e una
 * con controllo di propriet&agrave; per l'uso da parte dei giocatori.
 *
 * @see com.gameplatform.shared.domain.model.ReservationId
 */
public interface CancelReservationUseCase {

    /**
     * Cancella la prenotazione senza effettuare il controllo di propriet&agrave;.
     * Riservata ai percorsi interni come lo scheduler di scadenza prenotazioni
     * o per bypass amministrativo PLATFORM_ADMIN.
     *
     * @param reservationId identificativo della prenotazione da cancellare
     */
    void cancel(ReservationId reservationId);

    /**
     * Cancella la prenotazione dopo aver verificato che l'utente richiedente
     * sia il proprietario della prenotazione.
     *
     * @param reservationId  identificativo della prenotazione da cancellare
     * @param actingUserId   identificativo dell'utente che richiede la cancellazione
     */
    void cancel(ReservationId reservationId, UserId actingUserId);
}
