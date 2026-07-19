package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di una sessione di gioco all'interno della piattaforma.
 *
 * <p>Incapsula il valore stringa che distingue una {@code GameSession} dalle altre,
 * garantendo che l'identificatore sia sempre presente e non vuoto.</p>
 *
 * @see com.gameplatform.shared.domain.model.GameSession
 */
public record GameSessionId(String value) {

    /**
     * Costruisce un identificatore di sessione di gioco a partire dal valore fornito.
     *
     * <p>Il valore rappresenta la chiave univoca della sessione e non può essere
     * {@code null} né una stringa vuota o composta esclusivamente da spazi.</p>
     *
     * @param value il valore dell'identificatore di sessione; non deve essere {@code null},
     *              né vuoto, né composto soltanto da caratteri di spaziatura
     * @throws IllegalArgumentException se {@code value} è {@code null} o {@link String#isBlank() blank}
     */
    public GameSessionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GameSessionId cannot be null");
        }
    }
}
