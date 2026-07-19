package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gameplatform.local.domain.model.TournamentSummaryLocal;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentSummaryLocalJpaEntity;
import com.gameplatform.shared.domain.model.GameType;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Null-safe mapper between the {@link TournamentSummaryLocal} domain model and
 * the {@link TournamentSummaryLocalJpaEntity} persistence entity. The
 * {@code buildingIds} {@link List} is stored as a JSON {@link String} column
 * (mirror of the {@code registration_rules} handling in
 * {@link GameDefinitionLocalMapper}); {@code null} / empty lists are
 * serialised as {@code null} on the column. {@code JsonProcessingException} is
 * wrapped into a {@link RuntimeException} so it does not leak past the
 * adapter boundary.
 */
@Component
public class TournamentSummaryLocalMapper {

    private final ObjectMapper objectMapper;

    /**
     * Costruisce il mapper con l'ObjectMapper per la serializzazione JSON
     * della lista buildingIds.
     *
     * @param objectMapper il mapper JSON per serializzazione/deserializzazione (non null)
     */
    public TournamentSummaryLocalMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converte un'entità JPA {@link TournamentSummaryLocalJpaEntity} nel
     * corrispondente modello di dominio {@link TournamentSummaryLocal}.
     * Deserializza il campo JSON {@code buildingIds} da stringa a lista.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     * @throws RuntimeException in caso di errore di deserializzazione JSON
     */
    public TournamentSummaryLocal toDomain(TournamentSummaryLocalJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<String> buildingIds = List.of();
        if (entity.getBuildingIdsJson() != null && !entity.getBuildingIdsJson().isBlank()) {
            try {
                buildingIds = objectMapper.readValue(
                        entity.getBuildingIdsJson(),
                        new TypeReference<List<String>>() {
                        });
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to deserialize building_ids JSON for tournament "
                        + entity.getTournamentId(), e);
            }
        }
        Boolean deleted = entity.getDeleted() != null ? entity.getDeleted() : Boolean.FALSE;
        return new TournamentSummaryLocal(
                new TournamentId(entity.getTournamentId()),
                entity.getName(),
                GameType.valueOf(entity.getGameType()),
                entity.getTeamBased() != null && entity.getTeamBased(),
                entity.getTeamSize() != null ? entity.getTeamSize() : 0,
                TournamentStatus.valueOf(entity.getStatus()),
                entity.getStartsAt(),
                entity.getEndsAt(),
                buildingIds,
                entity.getParticipantsCount() != null ? entity.getParticipantsCount() : 0,
                deleted,
                entity.getUpdatedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link TournamentSummaryLocal} nella
     * corrispondente entità JPA {@link TournamentSummaryLocalJpaEntity}.
     * Serializza il campo {@code buildingIds} da lista a stringa JSON.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     * @throws RuntimeException in caso di errore di serializzazione JSON
     */
    public TournamentSummaryLocalJpaEntity toEntity(TournamentSummaryLocal domain) {
        if (domain == null) {
            return null;
        }
        String buildingIdsJson = null;
        if (domain.getBuildingIds() != null && !domain.getBuildingIds().isEmpty()) {
            try {
                buildingIdsJson = objectMapper.writeValueAsString(domain.getBuildingIds());
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize building_ids JSON for tournament "
                        + domain.getTournamentId().value(), e);
            }
        }
        return new TournamentSummaryLocalJpaEntity(
                domain.getTournamentId().value(),
                domain.getName(),
                domain.getGameType().name(),
                domain.isTeamBased(),
                domain.getTeamSize(),
                domain.getStatus().name(),
                domain.getStartsAt(),
                domain.getEndsAt(),
                buildingIdsJson,
                domain.getParticipantsCount(),
                domain.isDeleted(),
                domain.getUpdatedAt()
        );
    }
}
