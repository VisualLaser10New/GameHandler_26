package com.gameplatform.central.domain.exception;

/**
 * Eccezione marcatore per fallimenti HTTP transitori che devono essere sottoposti
 * a nuovo tentativo.
 *
 * <p>Estende {@link RuntimeException} ed e' utilizzata per distinguere gli errori
 * di comunicazione temporanei, la cui ripetizione e' opportuna, da quelli
 * definitivi.</p>
 *
 * @see RuntimeException
 */
public class TransientPushException extends RuntimeException {

    /**
     * Costruisce una nuova eccezione marcatore con il messaggio di dettaglio e la
     * causa specificati.
     *
     * @param message descrizione dell'errore; puo' essere {@code null}
     * @param cause   causa originaria dell'errore, recuperabile tramite
     *                {@link #getCause()}; non puo' essere {@code null} e se
     *                fornito {@code null} viene comunque registrato come causa
     * @see RuntimeException#RuntimeException(String, Throwable)
     */
    public TransientPushException(String message, Throwable cause) {
        super(message, cause);
    }
}
