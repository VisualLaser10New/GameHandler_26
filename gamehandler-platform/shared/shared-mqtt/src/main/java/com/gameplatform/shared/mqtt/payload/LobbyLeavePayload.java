package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT che trasporta i dati necessari per gestire l'abbandono di una lobby da parte di un utente.
 * Incapsula l'identificativo della sessione di gioco e l'identificativo dell'utente che esce,
 * ed è impiegato nei messaggi di comunicazione tra i componenti della piattaforma.
 *
 * @see LobbyJoinPayload
 * @see com.gameplatform.shared.mqtt.MqttTopics
 */
public record LobbyLeavePayload(
    /**
     * Restituisce l'identificativo della sessione di gioco da cui l'utente esce.
     *
     * @return l'identificativo della sessione; non è {@code null} e non è vuoto
     */
    String sessionId,

    /**
     * Restituisce l'identificativo dell'utente che abbandona la lobby.
     *
     * @return l'identificativo dell'utente; non è {@code null} e non è vuoto
     */
    String userId
) {}
