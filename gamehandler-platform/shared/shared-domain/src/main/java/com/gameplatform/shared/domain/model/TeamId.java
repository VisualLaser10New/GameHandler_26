package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di una squadra all'interno della piattaforma.
 * Incapsula il valore testuale che rappresenta l'identità di un team,
 * garantendo che non sia nullo né vuoto.
 *
 * @see com.gameplatform.shared.domain.model.Team
 */
public record TeamId(String value) {

    /**
     * Crea un nuovo identificatore di squadra a partire dal valore fornito.
     * Verifica che il valore non sia nullo né vuoto (blank) prima di istanziare il record.
     *
     * @param value il valore testuale dell'identificatore; non deve essere {@code null}
     *              né una stringa vuota o composta solo da spazi
     * @throws IllegalArgumentException se {@code value} è {@code null} o se è vuoto/blank
     */
    public TeamId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TeamId cannot be null");
        }
    }
}