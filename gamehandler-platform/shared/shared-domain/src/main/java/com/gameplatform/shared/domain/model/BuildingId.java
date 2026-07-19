package com.gameplatform.shared.domain.model;

/**
 * Identificatore univoco di un edificio all'interno della piattaforma di gioco.
 * Incapsula una stringa non nulla e non vuota che rappresenta la chiave di riferimento
 * di un edificio nel dominio condiviso.
 *
 * @see com.gameplatform.shared.domain.model.Building
 */
public record BuildingId(String id) {

    /**
     * Crea un nuovo identificatore di edificio a partire dal valore fornito.
     * Verifica che il valore non sia {@code null} e non sia una stringa vuota o composta
     * esclusivamente da spazi; in caso contrario rifiuta la creazione dell'identificatore.
     *
     * @param id il valore identificativo dell'edificio; non deve essere {@code null},
     *           né una stringa vuota né una stringa composta solo da caratteri di spaziatura
     * @throws IllegalArgumentException se {@code id} è {@code null}, vuoto o costituito
     *         unicamente da spazi
     */
    public BuildingId {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("BuildingId cannot be null");
    }
}
