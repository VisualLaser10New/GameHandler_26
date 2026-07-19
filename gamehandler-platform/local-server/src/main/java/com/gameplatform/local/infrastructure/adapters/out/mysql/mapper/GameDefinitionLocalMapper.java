package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.GameDefinitionLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.GameDefinitionLocalJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Null-safe mapper between the {@link GameDefinitionLocal} domain model and the
 * {@link GameDefinitionLocalJpaEntity} persistence entity. The
 * {@code registrationRules} {@link Map} is stored as a JSON {@link String} column;
 * {@code null} is preserved on both sides. {@code JsonProcessingException} is
 * wrapped into a {@link RuntimeException} so it does not leak past the
 * adapter boundary.
 */
@Component
public class GameDefinitionLocalMapper {

    private final ObjectMapper objectMapper;

    /**
     * Costruisce il mapper con l'ObjectMapper per la serializzazione JSON.
     *
     * @param objectMapper il mapper JSON per serializzazione/deserializzazione (non null)
     */
    public GameDefinitionLocalMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converte un'entità JPA {@link GameDefinitionLocalJpaEntity} nel corrispondente
     * modello di dominio {@link GameDefinitionLocal}. Deserializza il campo JSON
     * {@code registrationRules} da stringa a {@link Map}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     * @throws RuntimeException in caso di errore di deserializzazione JSON
     */
    public GameDefinitionLocal toDomain(GameDefinitionLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> registrationRules = null;
        if (entity.getRegistrationRulesJson() != null) {
            try {
                registrationRules = objectMapper.readValue(
                        entity.getRegistrationRulesJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize registration_rules JSON", e);
            }
        }
        return new GameDefinitionLocal(
                GameType.valueOf(entity.getGameType()),
                entity.getName(),
                entity.getMinPlayers(),
                entity.getMaxPlayers(),
                entity.getTeamAllowed(),
                registrationRules,
                entity.getUpdatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link GameDefinitionLocal} nella corrispondente
     * entità JPA {@link GameDefinitionLocalJpaEntity}. Serializza il campo
     * {@code registrationRules} da {@link Map} a stringa JSON.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     * @throws RuntimeException in caso di errore di serializzazione JSON
     */
    public GameDefinitionLocalJpaEntity toEntity(GameDefinitionLocal domain) {
        if (domain == null) {
            return null;
        }
        String registrationRulesJson = null;
        if (domain.getRegistrationRules() != null) {
            try {
                registrationRulesJson = objectMapper.writeValueAsString(domain.getRegistrationRules());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize registration_rules JSON", e);
            }
        }
        return new GameDefinitionLocalJpaEntity(
                domain.getGameType().name(),
                domain.getName(),
                domain.getMinPlayers(),
                domain.getMaxPlayers(),
                domain.isTeamAllowed(),
                registrationRulesJson,
                domain.getUpdatedAt()
        );
    }
}