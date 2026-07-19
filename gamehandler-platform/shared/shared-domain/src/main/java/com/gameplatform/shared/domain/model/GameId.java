package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di un gioco all'interno della piattaforma.
 *
 * <p>Incapsula la stringa che rappresenta l'identificativo e ne garantisce la validità
 * costruttiva, impedendo la creazione di un identificatore nullo o vuoto.</p>
 *
 * @see com.gameplatform.shared.domain.model.Game
 */
public record GameId(String id) {

    /**
     * Crea un nuovo identificatore di gioco a partire dalla stringa fornita.
     *
     * <p>Valida che il valore non sia {@code null} e non sia una stringa vuota o composta
     * esclusivamente da caratteri di spaziatura; in caso contrario rifiuta la creazione.</p>
     *
     * @param id la stringa che rappresenta l'identificatore del gioco
     * @throws IllegalArgumentException se {@code id} è {@code null}, vuoto oppure composto
     *         solo da spazi in bianco
     */
    public GameId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("GameId cannot be null");
    }
}
