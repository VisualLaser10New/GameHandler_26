package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di un utente all'interno della piattaforma.
 *
 * <p>Rappresenta il valore primitivo di dominio che incapsula l'identificativo
 * testuale di un utente, garantendo che non sia nullo né vuoto.</p>
 *
 * @see com.gameplatform.shared.domain.model.User
 */
public record UserId(String value) {

    /**
     * Crea un nuovo identificatore utente a partire dal valore fornito.
     *
     * <p>Verifica che il valore non sia {@code null} e non sia una stringa
     * vuota o composta esclusivamente da spazi, rifiutando altrimenti la creazione.</p>
     *
     * @param value il valore identificativo dell'utente; non deve essere {@code null},
     *              non deve essere vuoto e non deve essere composto solo da spazi
     * @throws IllegalArgumentException se {@code value} è {@code null}, vuoto o costituito
     *         unicamente da caratteri di spaziatura
     */
    public UserId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId cannot be null");
        }
    }
}
