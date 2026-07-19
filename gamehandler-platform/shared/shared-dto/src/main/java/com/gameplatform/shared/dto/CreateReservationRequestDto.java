package com.gameplatform.shared.dto;

import java.time.Instant;

/**
 * DTO di richiesta utilizzato per creare una nuova prenotazione di una postazione di gioco.
 * Trasporta i dati identificativi del gioco e dell'utente interessati, insieme all'intervallo
 * temporale di validità della prenotazione espresso tramite istanti UTC.
 *
 * @see com.gameplatform.shared.dto.ReservationDto
 */
public record CreateReservationRequestDto(
    String gameId,
    String userId,
    Instant startTime,
    Instant endTime
) {}
