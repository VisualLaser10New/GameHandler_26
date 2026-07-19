package com.gameplatform.shared.mqtt.payload;

/**
 * Payload utilizzato per richiedere la ripresa di una sessione di gioco
 * precedentemente sospesa o interrotta.
 *
 * <p>Incapsula l'identificativo univoco della sessione da riprendere e viene
 * impiegato come messaggio MQTT per il trasporto delle informazioni di ripresa.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.SessionStartPayload
 */
public record SessionResumePayload(
    /**
     * Restituisce l'identificativo univoco della sessione di gioco da riprendere.
     *
     * @return l'identificativo della sessione; puo' essere {@code null} se non valorizzato
     */
    String sessionId
) {}
