package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT utilizzato per comunicare l'annullamento di una lobby presso la piattaforma di gioco.
 * Incapsula l'identificativo della sessione di gioco e l'identificativo dell'utente che ha richiesto la cancellazione.
 *
 * @see com.gameplatform.shared.mqtt.payload.LobbyPayload
 */
public record LobbyCancelPayload(
    /**
     * Restituisce l'identificativo della sessione di gioco cui si riferisce l'annullamento.
     * Può essere {@code null} se non ancora valorizzato e non deve essere una stringa vuota per identificare una sessione valida.
     *
     * @return l'identificativo della sessione, possibilmente {@code null} o vuoto
     */
    String sessionId,

    /**
     * Restituisce l'identificativo dell'utente che ha richiesto l'annullamento della lobby.
     * Può essere {@code null} se non ancora valorizzato e non deve essere una stringa vuota per identificare un utente valido.
     *
     * @return l'identificativo dell'utente, possibilmente {@code null} o vuoto
     */
    String userId
) {}
