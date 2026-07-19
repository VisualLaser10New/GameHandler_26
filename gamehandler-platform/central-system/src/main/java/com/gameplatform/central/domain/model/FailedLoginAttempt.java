package com.gameplatform.central.domain.model;

import java.time.Instant;

/**
 * Record immutabile che rappresenta un singolo tentativo di accesso fallito,
 * identificato dal nome utente coinvolto e dall'istante in cui il tentativo è
 * avvenuto. Utilizzato per tracciare gli accessi non riusciti a fini di
 * sicurezza.
 *
 * @param username nome utente per cui è stato effettuato il tentativo; non può essere {@code null} né vuoto
 * @param attemptTime istante in cui è avvenuto il tentativo; non può essere {@code null}
 */
public record FailedLoginAttempt(String username, Instant attemptTime) {
    /**
     * Costruttore compatto che valida i componenti del record.
     *
     * @throws IllegalArgumentException se {@code username} è {@code null} o vuoto, oppure se {@code attemptTime} è {@code null}
     */
    public FailedLoginAttempt {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username cannot be null or blank");
        }
        if (attemptTime == null) {
            throw new IllegalArgumentException("attemptTime cannot be null");
        }
    }
}
