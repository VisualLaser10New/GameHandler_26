package com.gameplatform.central.infrastructure.adapters.out.mysql.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Convertitore JPA che rimuove i livelli di wrapping scalare di tipo stringa JSON
 * che il driver H2 2.x (in modalit&agrave; {@code MODE=MySQL}) aggiunge in lettura
 * quando una colonna {@code JSON} &egrave; associata a un semplice {@code String}.
 *
 * <p>Su MySQL il valore restituito non inizia mai con il carattere {@code "},
 * pertanto il convertitore si comporta come un'operazione trasparente senza effetti.
 * Su H2, invece, lo stesso valore pu&ograve; essere restituito come stringa JSON
 * annidata (ad esempio {@code "\"{\\\"userId\\\":...}\""}), condizione che impedirebbe
 * la corretta interpretazione del contenuto come nodo oggetto.</p>
 *
 * @see AttributeConverter
 */
@Converter
public class JsonStringUnwrappingConverter implements AttributeConverter<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Converte l'attributo dell'entit&agrave; nel valore da persistere sulla colonna
     * del database.
     *
     * <p>Il valore viene restituito inalterato: nessuna trasformazione &egrave;
     * applicata in questa direzione, poich&eacute; il wrapping scalare pu&ograve; essere
     * introdotto unicamente dal driver in fase di lettura.</p>
     *
     * @param attribute l'attributo dell'entit&agrave; di tipo {@link String} da convertire,
     *                  pu&ograve; essere {@code null}
     * @return il valore da scrivere sulla colonna del database, corrispondente
     *         all'attributo fornito (incluso {@code null})
     */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

    /**
     * Converte il valore letto dalla colonna del database nell'attributo
     * dell'entit&agrave;, rimuovendo eventuali livelli di wrapping scalare di tipo
     * stringa JSON aggiunti dal driver.
     *
     * <p>Il metodo svolge ripetutamente l'operazione di spacchettamento finch&eacute;
     * il valore inizia con il carattere {@code "}, ovvero finch&eacute; rappresenta una
     * stringa JSON. Su MySQL il valore non inizia mai con {@code "}, quindi il
     * ciclo non viene mai eseguito e il valore &egrave; restituito invariato.</p>
     *
     * @param dbData il valore grezzo letto dalla colonna del database, pu&ograve; essere
     *               {@code null} o una stringa vuota
     * @return l'attributo dell'entit&agrave; privato dei livelli di wrapping scalare,
     *         oppure {@code null} o la stringa vuota se il parametro in ingresso
     *         &egrave; {@code null} o vuoto
     * @see #convertToDatabaseColumn(String)
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        // H2 (MODE=MySQL) may wrap a JSON column value in one or more JSON-string
        // scalar layers on read-back (e.g. "\"{\\\"userId\\\":...}\""). Unwrap
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