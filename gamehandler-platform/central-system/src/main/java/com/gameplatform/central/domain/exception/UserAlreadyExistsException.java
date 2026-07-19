package com.gameplatform.central.domain.exception;

/**
 * Eccezione lanciata quando l'utente che si tenta di creare o registrare risulta
 * gia' esistente nel sistema.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per segnalare una
 * violazione del vincolo di univocita' sull'identita' dell'utente.</p>
 *
 * @see RuntimeException
 */
public class UserAlreadyExistsException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio specificato.
     *
     * @param message descrizione dell'errore; puo' essere {@code null} ma in tal
     *                caso il messaggio di dettaglio risultante sara' {@code null}
     * @see RuntimeException#RuntimeException(String)
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }

    /**
     * Costruisce una nuova eccezione con il messaggio di dettaglio e la causa specificati.
     *
     * @param message descrizione dell'errore; puo' essere {@code null}
     * @param cause   causa originaria dell'errore, recuperabile tramite
     *                {@link #getCause()}; puo' essere {@code null}
     * @see RuntimeException#RuntimeException(String, Throwable)
     */
    public UserAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
