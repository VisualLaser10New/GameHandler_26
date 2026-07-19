package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT utilizzato per segnalare l'avvio di una partita all'interno di una lobby.
 * Incapsula l'identificativo di sessione che lega il messaggio alla partita associata.
 *
 * @see com.gameplatform.shared.mqtt.payload.GamePayload
 */
public record LobbyStartPayload(
    /**
     * Restituisce l'identificativo della sessione di gioco associata alla lobby.
     *
     * @return l'identificativo della sessione; puo' essere {@code null} se non valorizzato
     */
    String sessionId
) {}
