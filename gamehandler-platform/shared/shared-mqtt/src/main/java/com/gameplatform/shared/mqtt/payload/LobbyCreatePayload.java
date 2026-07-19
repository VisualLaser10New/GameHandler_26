package com.gameplatform.shared.mqtt.payload;

import com.gameplatform.shared.domain.model.GameType;

/**
 * Payload utilizzato per richiedere la creazione di una lobby di gioco tramite il broker MQTT.
 * Incapsula il tipo di gioco da avviare e l'identificativo del giocatore che ne promuove la creazione.
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record LobbyCreatePayload(
    /**
     * Restituisce il tipo di gioco per il quale viene creata la lobby.
     *
     * @return il {@link GameType} associato alla lobby, non {@code null}
     */
    GameType gameType,

    /**
     * Restituisce l'identificativo del giocatore che ha richiesto la creazione della lobby.
     *
     * @return l'identificativo del creatore, non {@code null} e non vuoto
     */
    String creatorId
) {}
