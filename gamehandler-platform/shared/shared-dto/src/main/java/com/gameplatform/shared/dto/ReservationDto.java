package com.gameplatform.shared.dto;

import java.time.Instant;
import com.gameplatform.shared.domain.model.ReservationStatus;

/**
 * DTO che rappresenta una prenotazione di un gioco da parte di un utente.
 *
 * <p>Contiene i dati essenziali per identificare la prenotazione, il gioco e l'utente
 * interessati, lo stato corrente e l'intervallo temporale di validità.</p>
 *
 * @see com.gameplatform.shared.domain.model.ReservationStatus
 */
public record ReservationDto(
    /**
     * Identificativo univoco della prenotazione.
     *
     * @return l'identificativo della prenotazione, non deve essere {@code null}
     */
    String id,

    /**
     * Identificativo del gioco oggetto della prenotazione.
     *
     * @return l'identificativo del gioco, non deve essere {@code null}
     */
    String gameId,

    /**
     * Identificativo dell'utente che ha effettuato la prenotazione.
     *
     * @return l'identificativo dell'utente, non deve essere {@code null}
     */
    String userId,

    /**
     * Stato corrente della prenotazione.
     *
     * @return lo stato della prenotazione, non deve essere {@code null}
     * @see com.gameplatform.shared.domain.model.ReservationStatus
     */
    ReservationStatus status,

    /**
     * Istante di inizio della prenotazione.
     *
     * @return l'istante di inizio, non deve essere {@code null}
     */
    Instant startTime,

    /**
     * Istante di fine della prenotazione.
     *
     * @return l'istante di fine, non deve essere {@code null}
     */
    Instant endTime
) {}
