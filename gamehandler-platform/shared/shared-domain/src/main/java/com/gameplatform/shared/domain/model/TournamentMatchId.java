package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di un incontro di torneo all'interno della piattaforma.
 *
 * <p>Rappresenta il valore immutabile che distingue un {@code TournamentMatch} dagli altri,
 * garantendo che ogni incontro sia referenziabile in modo deterministico dai servizi di dominio.</p>
 *
 * @see TournamentMatch
 */
public record TournamentMatchId(String value) {

    /**
     * Costruisce un identificatore di incontro di torneo a partire dal valore fornito.
     *
     * <p>Il valore non può essere {@code null} né una stringa vuota o composta unicamente
     * da spazi in bianco, poiché tali condizioni non rappresentano un identificatore valido.</p>
     *
     * @param value il valore testuale dell'identificatore; non deve essere {@code null},
     *              né vuoto, né composto esclusivamente da caratteri di spaziatura
     * @throws IllegalArgumentException se {@code value} è {@code null} o {@link String#isBlank() blank}
     */
    public TournamentMatchId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TournamentMatchId cannot be null");
        }
    }
}