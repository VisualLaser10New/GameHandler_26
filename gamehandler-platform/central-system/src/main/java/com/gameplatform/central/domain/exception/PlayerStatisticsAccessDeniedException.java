package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando un chiamante richiede le statistiche di un altro
 * giocatore ({@code GET /api/players/{userId}/statistics}) senza essere ne' il
 * giocatore stesso ne' un {@code PLATFORM_ADMIN}.
 *
 * <p>Mappata a HTTP 403 Forbidden dal {@code GlobalExceptionHandler} del sistema
 * centrale.</p>
 *
 * @see RuntimeException
 */
public class PlayerStatisticsAccessDeniedException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public PlayerStatisticsAccessDeniedException(String message) {
        super(message);
    }
}
