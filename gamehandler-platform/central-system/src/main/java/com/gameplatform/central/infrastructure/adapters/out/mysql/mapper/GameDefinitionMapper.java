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
 * Null-safe mapper between the {@link GameDefinition} central domain model and
 * the {@link GameDefinitionJpaEntity} persistence entity. {@code @Component}
 * instance bean (matches {@code LocalAdminBuildingMapper} /
 * {@code StatisticsMapper}); converts the {@code game_type} String column
 * to/from the {@link GameType} enum NAME and serialises the
 * {@code registration_rules} JSON column (Map&harr;JSON-string) via the injected
 * {@link ObjectMapper}.
 */
@Component
public class GameDefinitionMapper {

    private final ObjectMapper objectMapper;

    public GameDefinitionMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
