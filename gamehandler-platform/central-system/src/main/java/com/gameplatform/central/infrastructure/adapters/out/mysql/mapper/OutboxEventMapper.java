package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.OutboxEvent;
import com.gameplatform.central.domain.model.OutboxEventStatus;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.OutboxEventJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link OutboxEvent} e l'entità persistente {@link OutboxEventJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, converte la colonna
 * {@code status} da/verso l'enum {@link OutboxEventStatus}.
 *
 * @see OutboxEvent
 * @see OutboxEventJpaEntity
 */
@Component
public class OutboxEventMapper {

    /**
     * Converte un'entità persistente {@link OutboxEventJpaEntity} nel
     * corrispondente modello di dominio {@link OutboxEvent}.
     * <p>
     * La colonna {@code status} viene convertita tramite
     * {@link OutboxEventStatus#valueOf(String)}; se {@code null} viene
     * mantenuta {@code null}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link OutboxEvent} o {@code null} se l'entità è {@code null}
     * @throws IllegalArgumentException se la stringa {@code status} non corrisponde a un valore {@link OutboxEventStatus} valido
     * @see #toEntity(OutboxEvent)
     */
    public OutboxEvent toDomain(OutboxEventJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new OutboxEvent(
                entity.getId(),
                entity.getEventType(),
                entity.getPayload(),
                entity.getStatus() != null ? OutboxEventStatus.valueOf(entity.getStatus()) : null,
                entity.getCreatedAt(),
                entity.getSentAt()
        );
    }

    /**
     * Converte un modello di dominio {@link OutboxEvent} nell'entità
     * persistente {@link OutboxEventJpaEntity} da persistere.
     * <p>
     * Lo {@link OutboxEventStatus} viene serializzato tramite
     * {@link Enum#name()}; se {@code null} viene mantenuto {@code null}.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link OutboxEventJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(OutboxEventJpaEntity)
     */
    public OutboxEventJpaEntity toEntity(OutboxEvent domain) {
        if (domain == null) {
            return null;
        }
        return new OutboxEventJpaEntity(
                domain.getId(),
                domain.getEventType(),
                domain.getPayload(),
                domain.getStatus() != null ? domain.getStatus().name() : null,
                domain.getCreatedAt(),
                domain.getSentAt()
        );
    }
}

