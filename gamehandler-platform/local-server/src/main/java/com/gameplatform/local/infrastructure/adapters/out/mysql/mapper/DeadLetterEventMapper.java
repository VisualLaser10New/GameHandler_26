package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.DeadLetterEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.DeadLetterEventJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper null-safe tra il modello di dominio {@link DeadLetterEvent} e l'entità
 * di persistenza {@link DeadLetterEventJpaEntity}. Fornisce la conversione
 * bidirezionale per eventi finiti nella coda dei messaggi non recapitabili.
 */
@Component
public class DeadLetterEventMapper {

    /**
     * Converte un'entità JPA {@link DeadLetterEventJpaEntity} nel corrispondente
     * modello di dominio {@link DeadLetterEvent}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
    public DeadLetterEvent toDomain(DeadLetterEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new DeadLetterEvent(
            entity.getId(),
            entity.getEventId(),
            entity.getEventType(),
            entity.getPayload(),
            entity.getOriginalStatus(),
            entity.getRetryCount(),
            entity.getReason(),
            entity.getPromotedAt()
        );
    }

    /**
     * Converte un modello di dominio {@link DeadLetterEvent} nella corrispondente
     * entità JPA {@link DeadLetterEventJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
    public DeadLetterEventJpaEntity toEntity(DeadLetterEvent domain) {
        if (domain == null) {
            return null;
        }
        return new DeadLetterEventJpaEntity(
            domain.getId(),
            domain.getEventId(),
            domain.getEventType(),
            domain.getPayload(),
            domain.getOriginalStatus(),
            domain.getRetryCount(),
            domain.getReason(),
            domain.getPromotedAt()
        );
    }
}
