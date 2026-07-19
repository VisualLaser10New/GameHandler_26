package com.gameplatform.shared.mqtt;

/**
 * Raccolta di costanti intere che definiscono i livelli di qualità del servizio (QoS)
 * utilizzati nei messaggi MQTT scambiati dalla piattaforma.
 *
 * <p>I valori rappresentano i livelli QoS previsti dal protocollo MQTT e sono condivisi
 * tra i componenti che pubblicano e sottoscrivono i topic del sistema.</p>
 *
 * @see com.gameplatform.shared.mqtt.MqttClient
 * @see com.gameplatform.shared.mqtt.MqttTopic
 */
public final class MqttQos {
    /**
     * Livello di qualità del servizio applicato ai messaggi di tipo {@code STATE}.
     *
     * <p>Il valore {@code 1} corrisponde al QoS "almeno una volta" (at least once):
     * il messaggio viene consegnato almeno una volta, senza garanzia di assenza di duplicati.</p>
     */
    public static final int STATE = 1;

    /**
     * Livello di qualità del servizio applicato ai messaggi di tipo {@code SESSION}.
     *
     * <p>Il valore {@code 1} corrisponde al QoS "almeno una volta" (at least once):
     * il messaggio viene consegnato almeno una volta, senza garanzia di assenza di duplicati.</p>
     */
    public static final int SESSION = 1;

    /**
     * Livello di qualità del servizio applicato ai messaggi di tipo {@code HEARTBEAT}.
     *
     * <p>Il valore {@code 0} corrisponde al QoS "al massimo una volta" (at most once):
     * il messaggio viene inviato senza conferma di ricezione, pertanto può andare perso.</p>
     */
    public static final int HEARTBEAT = 0;
}
