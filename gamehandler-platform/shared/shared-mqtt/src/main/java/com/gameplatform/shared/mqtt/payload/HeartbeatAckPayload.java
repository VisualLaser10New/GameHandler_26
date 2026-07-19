package com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

/**
 * Payload di conferma (acknowledge) inviato dal server in risposta a un heartbeat ricevuto da un client.
 * Trasporta l'identificativo della partita e l'istante temporale del server al momento della risposta.
 *
 * @see com.gameplatform.shared.mqtt.payload.HeartbeatPayload
 */
public record HeartbeatAckPayload(
    /**
     * Restituisce l'identificativo della partita a cui si riferisce la conferma.
     *
     * @return l'identificativo della partita, può essere {@code null} se non valorizzato
     */
    String gameId,

    /**
     * Restituisce l'istante temporale del server al momento dell'invio della conferma.
     *
     * @return l'istante del server, può essere {@code null} se non valorizzato
     */
    Instant serverTimestamp
) {}
