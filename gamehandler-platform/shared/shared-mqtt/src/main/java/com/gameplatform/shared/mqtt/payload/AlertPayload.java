package com.gameplatform.shared.mqtt.payload;

import java.time.Instant;

/**
 * Record che rappresenta il payload di un messaggio di allerta scambiato
 * attraverso il broker MQTT. Contiene le informazioni essenziali per
 * identificare e descrivere un evento di allerta relativo a una partita.
 *
 * @see com.gameplatform.shared.mqtt.payload.MqttPayload
 */
public record AlertPayload(
    /**
     * Restituisce il tipo di allerta associata al messaggio.
     *
     * @return il tipo di allerta, puo' essere {@code null} se non specificato
     */
    String alertType,

    /**
     * Restituisce l'identificativo della partita cui si riferisce l'allerta.
     *
     * @return l'identificativo della partita, puo' essere {@code null} se non associata
     */
    String gameId,

    /**
     * Restituisce il messaggio descrittivo dell'allerta.
     *
     * @return il messaggio dell'allerta, puo' essere {@code null} o una stringa vuota
     */
    String message,

    /**
     * Restituisce l'istante temporale in cui e' stata generata l'allerta.
     *
     * @return il timestamp dell'allerta, puo' essere {@code null} se non disponibile
     */
    Instant timestamp
) {}
