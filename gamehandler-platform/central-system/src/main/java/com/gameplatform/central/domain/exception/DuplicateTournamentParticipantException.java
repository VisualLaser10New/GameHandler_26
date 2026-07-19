package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando un singolo partecipante ({@code userId}) risulta gia'
 * registrato a un torneo e viene tentata una registrazione duplicata.
 *
 * <p>Mappata a HTTP 409.</p>
 *
 * @see RuntimeException
 */
public class DuplicateTournamentParticipantException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public DuplicateTournamentParticipantException(String message) {
        super(message);
    }
}
