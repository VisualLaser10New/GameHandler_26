package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Eccezione sollevata da {@link ApiClient} quando il server locale non è
 * raggiungibile (connessione rifiutata o timeout) oppure risponde con uno
 * stato {@code 5xx}.
 * <p>
 * Il livello UI mostra la pagina di errore globale con un callback per
 * il tentativo di riconnessione (PIANO §7.C riga 757).
 */
public class ServerUnavailableException extends RuntimeException {

    /**
     * Costruisce una {@code ServerUnavailableException} con il messaggio di
     * dettaglio specificato.
     *
     * @param message il messaggio di dettaglio (può essere {@code null}).
     */
    public ServerUnavailableException(String message) {
        super(message);
    }

    /**
     * Costruisce una {@code ServerUnavailableException} con il messaggio di
     * dettaglio e la causa specificati.
     *
     * @param message il messaggio di dettaglio (può essere {@code null}).
     * @param cause   la causa radice dell'errore (può essere {@code null};
     *                in tal caso il costruttore delegato a
     *                {@link RuntimeException#RuntimeException(String, Throwable)}
     *                non associa alcuna causa).
     */
    public ServerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
