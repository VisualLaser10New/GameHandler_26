package com.gameplatform.local.domain.ports.out;

import com.gameplatform.shared.mqtt.payload.AlertPayload;

/**
 * Porta outbound per la pubblicazione di messaggi di alert.
 * <p>
 * Consente di inviare notifiche di alert verso il sistema di messaggistica
 * (MQTT), tipicamente per comunicare stato di errore, manutenzione o
 * eventi rilevanti agli operatori della sede locale.
 * </p>
 *
 * @see AlertPayload
 */
public interface PublishAlertPort {
    /**
     * Pubblica un messaggio di alert sul canale appropriato.
     *
     * @param payload il payload contenente i dettagli dell'alert da pubblicare
     */
    void publishAlert(AlertPayload payload);
}
