package com.gameplatform.local.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.local.domain.model.OutboxEvent;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper null-safe tra il modello di dominio {@link OutboxEvent} e l'entità
 * di persistenza {@link OutboxEventJpaEntity}. Fornisce la conversione
 * bidirezionale per gli eventi del pattern outbox utilizzati nella
 * pubblicazione affidabile di messaggi.
 */
@Component
public class OutboxEventMapper {

    /**
     * Converte un'entità JPA {@link OutboxEventJpaEntity} nel corrispondente
     * modello di dominio {@link OutboxEvent}.
     *
     * @param entity l'entità JPA da convertire, può essere {@code null}
     * @return il modello di dominio, oppure {@code null} se l'input è {@code null}
     */
    public OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OutboxEvent(
            entity.getId(),
            entity.getEventType(),
            entity.getPayload(),
            entity.getStatus(),
            entity.getCreatedAt(),
            entity.getSentAt(),
            entity.getRetryCount()
        );
    }

    /**
     * Converte un modello di dominio {@link OutboxEvent} nella corrispondente
     * entità JPA {@link OutboxEventJpaEntity}.
     *
     * @param domain il modello di dominio da convertire, può essere {@code null}
     * @return l'entità JPA, oppure {@code null} se l'input è {@code null}
     */
    public OutboxEventJpaEntity toEntity(OutboxEvent domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxEventJpaEntity(
            domain.getId(),
            domain.getEventType(),
            domain.getPayload(),
            domain.getStatus(),
            domain.getCreatedAt(),
            domain.getSentAt(),
            domain.getRetryCount()
        );
    }
}
