package com.gameplatform.client.domain.exception;

import com.gameplatform.client.infrastructure.rest.ApiClient;

/**
 * Eccezione sollevata da {@link ApiClient} quando il server locale risponde
 * con uno stato {@code 4xx} diverso da 401 o 403 (ad esempio 400 Bad Request,
 * 404 Not Found, 409 Conflict).
 * <p>
 * Trasporta il codice di stato numerico ({@code statusCode}) e il corpo della
 * risposta ({@code body}) in modo che il livello UI possa generare un messaggio
 * leggibile specifico per il dominio invece di una semplice stringa
 * {@code "HTTP <codice>"}.
 * <p>
 * {@link #getMessage()} mantiene il formato precedente
 * {@code "HTTP <codice> — body=<corpo>"} per garantire la compatibilità con i
 * rami esistenti che analizzano la stringa in altre viste.
 */
public class HttpClientResponseException extends RuntimeException {

    private final int statusCode;
    private final String body;

    /**
     * Costruisce una {@code HttpClientResponseException} con il codice di
     * stato HTTP e il corpo della risposta specificati.
     * <p>
     * Il messaggio ereditato da {@link RuntimeException} viene formattato come
     * {@code "HTTP <statusCode> — body=<body>"}. Se {@code body} è
     * {@code null}, viene convertito in una stringa vuota.
     *
     * @param statusCode il codice di stato HTTP della risposta.
     * @param body       il corpo della risposta HTTP (può essere {@code null};
     *                   in tal caso viene internamente convertito in
     *                   stringa vuota).
     */
    public HttpClientResponseException(int statusCode, String body) {
        super("HTTP " + statusCode + " — body=" + (body == null ? "" : body));
        this.statusCode = statusCode;
        this.body = body == null ? "" : body;
    }

    /**
     * Restituisce il codice di stato HTTP della risposta che ha causato
     * l'eccezione.
     *
     * @return il codice di stato HTTP (ad esempio 400, 404, 409).
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Restituisce il corpo della risposta HTTP che ha causato l'eccezione.
     *
     * @return il corpo della risposta; mai {@code null} (una stringa vuota
     *         se il corpo originale era {@code null}).
     */
    public String getBody() {
        return body;
    }
}
