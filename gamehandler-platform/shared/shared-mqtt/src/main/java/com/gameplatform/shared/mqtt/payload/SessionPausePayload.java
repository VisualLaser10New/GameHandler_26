package com.gameplatform.shared.mqtt.payload;

/**
 * Payload MQTT che trasporta i dati necessari per segnalare la sospensione di una sessione di gioco.
 *
 * <p>Contiene l'identificativo della sessione interessata e l'entità che ha richiesto la pausa.</p>
 *
 * @see com.gameplatform.shared.mqtt.payload.SessionResumePayload
 */
public record SessionPausePayload(
    /**
     * Restituisce l'identificativo della sessione di gioco da sospendere.
     *
     * @return l'identificativo della sessione; puo' essere {@code null} o una stringa vuota se non valorizzato
     */
    String sessionId,

    /**
     * Restituisce l'identificativo dell'entita' che ha richiesto la sospensione della sessione.
     *
     * @return l'identificativo di chi ha effettuato la pausa; puo' essere {@code null} o una stringa vuota se non valorizzato
     */
    String pausedBy
) {}
