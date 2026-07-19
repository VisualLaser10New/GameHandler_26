package com.gameplatform.central.infrastructure.adapters.out.mysql.mapper;

import com.gameplatform.central.domain.model.ReplicationProgress;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.ReplicationProgressJpaEntity;
import org.springframework.stereotype.Component;

/**
 * Mapper senza stato (null-safe) tra il modello di dominio centrale
 * {@link ReplicationProgress} e l'entità persistente
 * {@link ReplicationProgressJpaEntity}.
 * <p>
 * Esposto come bean Spring {@code @Component}, genera l'identificativo
 * composito dell'entità concatenando {@code eventId} e {@code serverId}
 * con un trattino basso.
 *
 * @see ReplicationProgress
 * @see ReplicationProgressJpaEntity
 */
@Component
public class ReplicationProgressMapper {

    /**
     * Converte un'entità persistente {@link ReplicationProgressJpaEntity} nel
     * corrispondente modello di dominio {@link ReplicationProgress}.
     *
     * @param entity l'entità persistente di origine; se {@code null} restituisce {@code null}
     * @return il modello di dominio {@link ReplicationProgress} o {@code null} se l'entità è {@code null}
     * @see #toEntity(ReplicationProgress)
     */
    public ReplicationProgress toDomain(ReplicationProgressJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ReplicationProgress(entity.getEventId(), entity.getServerId());
    }

    /**
     * Converte un modello di dominio {@link ReplicationProgress} nell'entità
     * persistente {@link ReplicationProgressJpaEntity} da persistere.
     * <p>
     * L'identificativo composito viene generato concatenando
     * {@code eventId} e {@code serverId} con un trattino basso.
     *
     * @param domain il modello di dominio di origine; se {@code null} restituisce {@code null}
     * @return l'entità persistente {@link ReplicationProgressJpaEntity} o {@code null} se il dominio è {@code null}
     * @see #toDomain(ReplicationProgressJpaEntity)
     */
    public ReplicationProgressJpaEntity toEntity(ReplicationProgress domain) {
        if (domain == null) {
            return null;
        }
        String id = domain.eventId() + "_" + domain.serverId();
        return new ReplicationProgressJpaEntity(id, domain.eventId(), domain.serverId());
    }
}
