package com.gameplatform.local.infrastructure.adapters.out.mqtt;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Outbound-message deduplication cache: suppresses MQTT loopback echoes the
 * local-server would otherwise receive on its own subscribed topics because
 * MQTT 3.1.1 has no {@code noLocal} flag.
 *
 * <p>Before every {@code MqttClient.publish(topic, message)} the
 * {@link MqttPublisherAdapter} calls {@link #recordOutbound(String, byte[])} so
 * the fingerprint (topic + SHA-256(payload)) and its TTL expiry are stored.
 * Every subscription callback in {@code MqttConfig} invokes
 * {@link #isRecentOutbound(String, byte[])} as its first line; when the inbound
 * message matches a recently-recorded outbound the listener returns early and
 * no further (deserialization) logic runs — fixing the
 * {@code UnrecognizedPropertyException} that occurred when the server received
 * its own {@code SessionStartPayload} broadcast-back on the {@code lobby/create}
 * and {@code lobby/start} inbound topics (root cause: the echo was deserialised
 * as {@code LobbyCreatePayload}/{@code LobbyStartPayload}, which lacks the
 * extra {@code sessionId}/{@code gameType} fields).
 *
 * <p>Thread-safe via {@link ConcurrentHashMap}; expired entries are evicted
 * inline during {@link #isRecentOutbound(String, byte[])} queries. Edge case
 * (documented in the project README and the bug report): two distinct publishes
 * with identical bytes within the TTL would cause the second one to be dropped,
 * but the broadcast-back payloads and the client inbound payloads differ in
 * fields, so a collision is effectively impossible.</p>
 */
@Component
public class OutboundMessageDeduplicationCache {

    /**
     * Durata di vita in millisecondi per ogni impronta registrata nella cache.
     * Trascorso questo intervallo l'entry viene considerata scaduta e rimossa.
     */
    static final long TTL_MS = 10_000L;

    /**
     * Mappa concorrente che associa l'impronta del messaggio (topic + SHA-256 del payload)
     * al timestamp di scadenza.
     */
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    /**
     * Registra l'invio di un messaggio in uscita calcolandone l'impronta tramite
     * {@link #fingerprint(String, byte[])} e memorizzandola con scadenza pari a
     * {@link #TTL_MS} millisecondi dal momento corrente. Se {@code topic} o
     * {@code bytes} sono {@code null} l'operazione viene ignorata.
     *
     * @param topic  topic MQTT su cui il messaggio è stato pubblicato
     * @param bytes  payload del messaggio in bytes
     */
    public void recordOutbound(String topic, byte[] bytes) {
        if (topic == null || bytes == null) {
            return;
        }
        String key = fingerprint(topic, bytes);
        cache.put(key, System.currentTimeMillis() + TTL_MS);
    }

    /**
     * Verifica se un messaggio in arrivo corrisponde a un messaggio inviato
     * recentemente dallo stesso nodo, basandosi sull'impronta del topic e del payload.
     * Gli elementi scaduti vengono rimossi dalla cache prima della ricerca.
     *
     * @param topic  topic MQTT del messaggio in arrivo
     * @param bytes  payload del messaggio in arrivo in bytes
     * @return {@code true} se il messaggio è stato pubblicato recentemente dallo stesso nodo,
     *         {@code false} altrimenti o se {@code topic} o {@code bytes} sono {@code null}
     */
    public boolean isRecentOutbound(String topic, byte[] bytes) {
        evictExpired();
        if (topic == null || bytes == null) {
            return false;
        }
        String key = fingerprint(topic, bytes);
        Long expiry = cache.get(key);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() > expiry) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Rimuove dalla cache tutte le entry il cui timestamp di scadenza è precedente
     * all'istante corrente. Metodo invocato internamente da
     * {@link #isRecentOutbound(String, byte[])}.
     */
    private void evictExpired() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(e -> now > e.getValue());
    }

    /**
     * Calcola l'impronta digitale di un messaggio come concatenazione del topic e del payload
     * sottoposta a hash SHA-256, restituita in formato esadecimale.
     *
     * @param topic  topic MQTT del messaggio
     * @param bytes  payload del messaggio in bytes
     * @return impronta SHA-256 in formato esadecimale
     * @throws RuntimeException se l'algoritmo SHA-256 non è disponibile nel JRE corrente
     */
    private String fingerprint(String topic, byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(topic.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            byte[] digest = md.digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available in this JDK", e);
        }
    }
}
