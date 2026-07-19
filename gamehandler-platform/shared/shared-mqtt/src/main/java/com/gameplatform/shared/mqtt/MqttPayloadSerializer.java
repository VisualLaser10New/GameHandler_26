package com.gameplatform.shared.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * Fornisce utility per la serializzazione e la deserializzazione dei payload dei messaggi MQTT
 * scambiati tra i componenti della piattaforma.
 *
 * <p>Il formato utilizzato è JSON, con supporto per le date in formato ISO-8601 e per il
 * polimorfismo dei risultati di gioco tramite il mix-in {@code GameResultMixIn}. Le proprietà
 * sconosciute presenti nel payload non causano errori in fase di deserializzazione.</p>
 *
 * @see com.gameplatform.shared.domain.result.GameResult
 */
public final class MqttPayloadSerializer {

   @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.ChessResult.class, name = "CHESS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.DartsResult.class, name = "DARTS"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.FoosballResult.class, name = "FOOSBALL"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.MonopolyResult.class, name = "MONOPOLY"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RiskResult.class, name = "RISK"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.RouletteResult.class, name = "ROULETTE"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.SlotResult.class, name = "SLOT"),
        @JsonSubTypes.Type(value = com.gameplatform.shared.domain.result.TeamResult.class, name = "TEAM")
    })
    private interface GameResultMixIn {}

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .addMixIn(com.gameplatform.shared.domain.result.GameResult.class, GameResultMixIn.class)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private MqttPayloadSerializer() {}

    /**
     * Converte un oggetto nel corrispondente payload JSON codificato come array di byte.
     *
     * <p>Il metodo gestisce oggetti di qualsiasi tipo, inclusi i risultati di gioco polimorfici,
     * producendo un payload pronto per la pubblicazione su un broker MQTT.</p>
     *
     * @param obj l'oggetto da serializzare; non deve essere {@code null}
     * @return l'array di byte contenente la rappresentazione JSON dell'oggetto; non è {@code null}
     *         e non è vuoto per un oggetto valido
     * @throws RuntimeException se la serializzazione fallisce (ad esempio per un grafo di oggetti
     *         non serializzabile o per {@code obj} non {@code null} ma non convertibile in JSON)
     * @see #deserialize(byte[], Class)
     */
    public static byte[] serialize(Object obj) {
        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize object to JSON payload", e);
        }
    }

    /**
     * Converte un payload JSON, codificato come array di byte, nell'istanza del tipo specificato.
     *
     * <p>Il metodo ricostruisce l'oggetto a partire dal payload ricevuto da un broker MQTT,
     * supportando i risultati di gioco polimorfici e ignorando le proprietà sconosciute.</p>
     *
     * @param data l'array di byte contenente il payload JSON da deserializzare; non deve essere
     *             {@code null} e deve rappresentare un JSON valido per il tipo richiesto
     * @param clazz la classe di destinazione dell'oggetto da ottenere; non deve essere {@code null}
     * @param <T> il tipo dell'oggetto restituito
     * @return l'istanza deserializzata di tipo {@code T}; non è {@code null} se la conversione ha successo
     * @throws RuntimeException se la deserializzazione fallisce (ad esempio per {@code data} non valido,
     *         vuoto o non conforme alla struttura di {@code clazz})
     * @see #serialize(Object)
     */
    public static <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON payload to " + clazz.getSimpleName(), e);
        }
    }
}
