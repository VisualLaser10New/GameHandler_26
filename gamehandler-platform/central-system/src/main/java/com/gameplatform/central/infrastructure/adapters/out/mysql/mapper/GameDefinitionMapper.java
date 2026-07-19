package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.central.domain.model.GameDefinition;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.GameDefinitionJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link GameDefinition} e l'entità persistente {@link GameDefinitionJpaEntity}.
 * Esposto come bean Spring {@code @Component}, converte la colonna String
 * {@code game_type} da/verso l'enum {@link GameType} e serializza/deserializza
 * la colonna JSON {@code registration_rules} (Map&harr;stringa JSON) tramite
 * l'{@link ObjectMapper} iniettato.
 *
 * @see GameDefinition
 * @see GameDefinitionJpaEntity
 */
@Component
public class GameDefinitionMapper {

    private final ObjectMapper objectMapper;

    public GameDefinitionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converte un'entità persistente {@link GameDefinitionJpaEntity} nel corrispondente
     * modello di dominio {@link GameDefinition}.
     *
     * <p>La colonna {@code game_type} viene tradotta tramite {@link com.gameplatform.shared.domain.model.GameType#valueOf(String)}
     * e la colonna JSON {@code registration_rules} viene deserializzata in una {@link Map}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link GameDefinition} o {@code null} se l'entità è {@code null}
     * @throws RuntimeException se la colonna {@code registration_rules} contiene un JSON non valido
     * @see #toEntity(GameDefinition)
     */
    public GameDefinition toDomain(GameDefinitionJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> registrationRules = null;
        String json = entity.getRegistrationRulesJson();
        if (json != null && !json.isBlank()) {
            try {
                registrationRules = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize registration_rules JSON: ", e);
            }
        }
        return new GameDefinition(
                GameType.valueOf(entity.getGameType()),
                entity.getName(),
                entity.getMinPlayers(),
                entity.getMaxPlayers(),
                entity.getTeamAllowed(),
                registrationRules,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link GameDefinition} nell'entità persistente
     * {@link GameDefinitionJpaEntity} da persistere.
     *
     * <p>Il {@link com.gameplatform.shared.domain.model.GameType} viene serializzato tramite
     * {@link Enum#name()} e la mappa {@code registration_rules} viene serializzata in una stringa JSON.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link GameDefinitionJpaEntity} o {@code null} se il dominio è {@code null}
     * @throws RuntimeException se la mappa {@code registration_rules} non può essere serializzata in JSON
     * @see #toDomain(GameDefinitionJpaEntity)
     */
    public GameDefinitionJpaEntity toEntity(GameDefinition domain) {
        if (domain == null) {
            return null;
        }
        String registrationRulesJson = null;
        Map<String, Object> rules = domain.getRegistrationRules();
        if (rules != null) {
            try {
                registrationRulesJson = objectMapper.writeValueAsString(rules);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize registration_rules JSON: ", e);
            }
        }
        return new GameDefinitionJpaEntity(
                domain.getGameType().name(),
                domain.getName(),
                domain.getMinPlayers(),
                domain.getMaxPlayers(),
                domain.isTeamAllowed(),
                registrationRulesJson,
                domain.getCreatedAt(),
                domain.getUpdatedAt()
        );
    }
}
