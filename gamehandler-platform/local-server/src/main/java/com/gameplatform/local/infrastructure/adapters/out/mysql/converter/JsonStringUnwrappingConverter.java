package com.gameplatform.local.infrastructure.adapters.out.mysql.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converter JPA che risolve il problema dell'incapsulamento in stringhe JSON
 * scalari aggiunto da H2 2.x (MODE=MySQL) durante la lettura di colonne di
 * tipo {@code JSON} mappate come semplici {@code String}.
 *
 * <p>Senza questo converter, H2 memorizza un valore come
 * {@code {"userId":"..."}} trasformandolo in
 * {@code "{\"userId\":\"...\"}"} (una stringa JSON scalare). Il codice
 * che utilizza {@code objectMapper.readTree(payload)} riceve quindi un
 * {@code TextNode} invece di un {@code ObjectNode}, causando l'ignoranza
 * silenziosa dell'evento.</p>
 *
 * <p>In ambiente MySQL il valore non inizia mai con il carattere {@code "},
 * pertanto il converter si comporta come un no-op trasparente.</p>
 *
 * @see AttributeConverter
 * @see ObjectMapper
 * @see JsonNode
 */
@Converter
public class JsonStringUnwrappingConverter implements AttributeConverter<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Converte l'attributo dell'entità per il salvataggio nella colonna
     * del database.
     *
     * <p>Restituisce il valore ricevuto senza alcuna trasformazione,
     * poiché non è richiesta alcuna modifica per la scrittura su database.</p>
     *
     * @param attribute il valore JSON da salvare, può essere {@code null}
     * @return il valore ricevuto senza alcuna modifica
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    /**
     * Converte il dato proveniente dalla colonna del database nell'attributo
     * dell'entità, rimuovendo eventuali livelli di incapsulamento in stringa
     * JSON scalare introdotti da H2 durante la lettura.
     *
     * <p>Il metodo analizza ricorsivamente il valore ricevuto: se inizia con
     * il carattere {@code "} e, dopo il parsing JSON, risulta essere un nodo
     * testuale, estrae il contenuto e ripete il controllo fino a ottenere un
     * valore non più incapsulato. In ambiente MySQL il ciclo non viene mai
     * eseguito perché il dato non inizia mai con {@code "}.</p>
     *
     * @param dbData il dato grezzo dalla colonna del database, può essere
     *               {@code null}
     * @return il valore JSON privo di incapsulamenti scalari, oppure
     *         {@code null} o la stringa vuota se ricevuti in input
     * @see JsonNode#isTextual()
     * @see ObjectMapper#readTree(String)
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        // H2 (MODE=MySQL) may wrap a JSON column value in one or more JSON-string
        // scalar layers on read-back (e.g. "\"{\\\"gameType\\\":...}\""). Unwrap
        // repeatedly until the value is no longer a JSON string scalar. On MySQL
        // the value never starts with '"' so this loop is a transparent no-op.
        String current = dbData;
        while (current != null && !current.isEmpty() && current.charAt(0) == '"') {
            try {
                JsonNode node = MAPPER.readTree(current);
                if (node.isTextual()) {
                    String unwrapped = node.asText();
                    if (unwrapped != null && !unwrapped.equals(current)) {
                        current = unwrapped;
                        continue;
                    }
                }
            } catch (Exception ignored) {
            }
            break;
        }
        return current;
    }
}