package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT utilizzato per richiedere l'ingresso di un utente all'interno di
 * una lobby di gioco.
 *
 * <p>Contiene l'identificativo della sessione di gioco e l'identificativo
 * dell'utente che effettua la richiesta di join.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.LobbyLeavePayload
 */
public record LobbyJoinPayload(
    /**
     * Restituisce l'identificativo della sessione di gioco a cui l'utente
     * intende accedere.
     *
     * @return l'identificativo della sessione; puo' essere {@code null} o
     *         una stringa vuota se non valorizzato
     */
    String sessionId,

    /**
     * Restituisce l'identificativo dell'utente che richiede l'ingresso nella
     * lobby.
     *
     * @return l'identificativo dell'utente; puo' essere {@code null} o una
     *         stringa vuota se non valorizzato
     */
    String userId
) {}
