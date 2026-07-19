package com.gameplatform.shared.dto;

import java.util.List;

/**
 * Rappresenta il carico utile di sincronizzazione inviato per trasmettere al client
 * gli eventi in attesa relativi a un determinato edificio di gioco.
 * Incapsula l'identificativo dell'edificio e l'elenco degli eventi da applicare.
 *
 * @see OutboxEventDto
 */
public record SyncPayloadDto(
    /**
     * Identificativo univoco dell'edificio di gioco a cui si riferiscono gli eventi.
     * Non deve essere {@code null} e non deve essere vuoto.
     */
    String buildingId,

    /**
     * Elenco degli eventi da sincronizzare per l'edificio indicato.
     * Non deve essere {@code null}; può essere vuoto nel caso in cui non vi siano
     * eventi pendenti da trasmettere.
     */
    List<OutboxEventDto> events
) {

    /**
     * Costruisce un nuovo payload di sincronizzazione a partire dall'identificativo
     * dell'edificio e dalla lista di eventi associati.
     *
     * @param buildingId identificativo univoco dell'edificio di gioco; non deve essere
     *                   {@code null} né vuoto
     * @param events     elenco degli eventi da sincronizzare; non deve essere {@code null},
     *                   può essere vuoto se non vi sono eventi pendenti
     */
    public SyncPayloadDto {
    }

    /**
     * Restituisce l'identificativo univoco dell'edificio di gioco a cui si riferiscono
     * gli eventi contenuti nel payload.
     *
     * @return l'identificativo dell'edificio; non {@code null} e non vuoto
     */
    public String buildingId() {
        return buildingId;
    }

    /**
     * Restituisce l'elenco degli eventi da sincronizzare per l'edificio indicato.
     *
     * @return la lista degli eventi; non {@code null}, può essere vuota se non vi sono
     *         eventi pendenti
     */
    public List<OutboxEventDto> events() {
        return events;
    }
}
