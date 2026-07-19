package com.gameplatform.local.domain.ports.out;

import com.gameplatform.local.domain.model.Reservation;
import com.gameplatform.shared.domain.model.GameId;
import com.gameplatform.shared.domain.model.ReservationId;
import com.gameplatform.shared.domain.model.ReservationStatus;
import com.gameplatform.shared.domain.model.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository out-port per la gestione delle prenotazioni delle macchine da gioco.
 * <p>
 * Fornisce operazioni CRUD e di ricerca per le prenotazioni effettuate dagli
 * utenti sulle macchine da gioco della sede locale. Supporta la ricerca per
 * utente, macchina, stato e scadenza.
 * </p>
 *
 * @see Reservation
 * @see ReservationStatus
 */
public interface ReservationRepository {
    /**
     * Salva o aggiorna una prenotazione.
     *
     * @param reservation la prenotazione da persistere
     * @return la prenotazione persistita
     */
    Reservation save(Reservation reservation);

    /**
     * Cerca una prenotazione in base al suo identificativo.
     *
     * @param id l'identificativo della prenotazione
     * @return un {@code Optional} contenente la prenotazione, vuoto se non trovata
     */
    Optional<Reservation> findById(ReservationId id);

    /**
     * Restituisce tutte le prenotazioni effettuate da un determinato utente.
     *
     * @param userId l'identificativo dell'utente
     * @return la lista delle prenotazioni dell'utente specificato
     */
    List<Reservation> findByUserId(UserId userId);

    /**
     * Restituisce tutte le prenotazioni per una determinata macchina da gioco.
     *
     * @param gameId l'identificativo della macchina da gioco
     * @return la lista delle prenotazioni per la macchina specificata
     */
    List<Reservation> findByGameId(GameId gameId);

    /**
     * Restituisce tutte le prenotazioni con un determinato stato.
     *
     * @param status lo stato delle prenotazioni da filtrare
     * @return la lista delle prenotazioni con lo stato specificato
     */
    List<Reservation> findByStatus(ReservationStatus status);

    /**
     * Restituisce tutte le prenotazioni scadute rispetto al momento specificato.
     *
     * @param now il momento di riferimento per la verifica della scadenza
     * @return la lista delle prenotazioni scadute
     */
    List<Reservation> findExpired(Instant now);

    /**
     * Conta il numero di prenotazioni associate a un insieme di macchine da gioco.
     *
     * @param gameIds la lista degli identificativi delle macchine da gioco
     * @return il numero di prenotazioni per le macchine specificate
     */
    int countByGameIds(List<GameId> gameIds);
}
