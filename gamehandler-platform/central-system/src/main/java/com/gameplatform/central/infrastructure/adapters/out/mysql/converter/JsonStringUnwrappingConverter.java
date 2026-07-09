package com.gameplatform.central.infrastructure.adapters.out.mysql.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Unwraps the JSON-string-scalar layer H2 2.x (MODE=MySQL) adds when reading
 * back a {@code JSON} column bound as a plain {@code String}. On MySQL the
 * value never starts with {@code "} so this converter is a transparent no-op.
 *
 * <p>Without this converter, H2 stores {@code {"userId":"..."}} as
 * {@code "{\"userId\":\"...\"}"} (a JSON string scalar), and the production
 * code's {@code objectMapper.readTree(payload)} sees a {@code TextNode}
 * instead of an {@code ObjectNode}, silently skipping the event.</p>
 */
@Converter
public class JsonStringUnwrappingConverter implements AttributeConverter<String, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute;
    }

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