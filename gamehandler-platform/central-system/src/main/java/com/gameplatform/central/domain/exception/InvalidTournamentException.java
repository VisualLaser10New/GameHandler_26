package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata dai servizi di torneo e di registrazione quando un comando
 * in ingresso viola un invariante di creazione o di registrazione: discrepanza
 * di policy {@code teamBased}/team, un numero di {@code buildingId} inferiore a
 * due, incoerenza del {@code teamSize}, {@code gameType} sconosciuto, richiesta
 * di team su un torneo individuale, capitano assente dai membri, mancata
 * corrispondenza del numero di membri o principal non risolto. Mappata a HTTP 400.
 *
 * @see RuntimeException
 */
public class InvalidTournamentException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public InvalidTournamentException(String message) {
        super(message);
    }
}
