package com.gameplatform.local.domain.exception;

/**
 * Eccezione lanciata quando si verifica un conflitto di stato
 * dovuto a accessi concorrenti ai dati del dominio. Garantisce
 * l'integrità delle operazioni in scenari di concorrenza.
 */
public class ConcurrentStateException extends RuntimeException {
    /**
     * Costruisce un'eccezione con il messaggio di dettaglio specificato.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     */
    public ConcurrentStateException(String message) {
        super(message);
    }

    /**
     * Costruisce un'eccezione con il messaggio di dettaglio e la causa specificati.
     *
     * @param message il messaggio che descrive il motivo dell'eccezione
     * @param cause   la causa originaria dell'eccezione
     */
    public ConcurrentStateException(String message, Throwable cause) {
        super(message, cause);
    }
}