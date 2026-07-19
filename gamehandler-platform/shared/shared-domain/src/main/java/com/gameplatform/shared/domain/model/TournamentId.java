package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di un torneo all'interno della piattaforma.
 *
 * <p>Incapsula il valore testuale che referenzia un torneo, garantendone la validit&agrave;
 * tramite il rifiuto di valori nulli o vuoti.</p>
 *
 * @see com.gameplatform.shared.domain.model.Tournament
 */
public record TournamentId(String value) {

    /**
     * Crea un nuovo identificatore di torneo a partire dal valore fornito.
     *
     * <p>Il valore rappresenta il riferimento testuale del torneo e non pu&ograve; essere
     * nullo n&eacute; vuoto (comprese le stringhe composte solo da spazi).</p>
     *
     * @param value il valore testuale dell'identificatore del torneo; non deve essere
     *              {@code null} n&eacute; una stringa vuota o composta esclusivamente da spazi
     * @throws IllegalArgumentException se {@code value} &egrave; {@code null} o una stringa vuota o
     *                                  composta solo da spazi
     */
    public TournamentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TournamentId cannot be null");
        }
    }
}