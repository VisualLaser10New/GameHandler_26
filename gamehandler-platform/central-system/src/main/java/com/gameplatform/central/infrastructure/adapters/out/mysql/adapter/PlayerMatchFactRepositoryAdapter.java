package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.PlayerMatchFact;
import com.gameplatform.central.domain.ports.out.PlayerMatchFactRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.PlayerMatchFactMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.PlayerMatchFactJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

/**
 * JPA adapter for the {@link PlayerMatchFactRepository} port. Matches the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape: constructor-injects the JPA repository + mapper.
 *
 * <p>{@link #saveIfAbsent} inserts the fact via the {@link EntityManager}
 * directly (rather than the Spring Data repository proxy) so that, in the
 * pathological case of a duplicate (session, user) pair, any
 * {@code PersistenceException} is observed before it can cross a transactional
 * proxy boundary and mark the caller's transaction rollback-only. A
 * pre-existence probe makes the common path idempotent without persisting a
 * known-existing row. The method is not annotated {@code @Transactional}: it
 * always participates in the caller's transaction (the
 * {@code SyncEventProcessor#processOne} {@code REQUIRES_NEW} transaction for
 * FASE 3 projection).</p>
 */
@Component
public class PlayerMatchFactRepositoryAdapter implements PlayerMatchFactRepository {

    private final PlayerMatchFactJpaRepository jpaRepository;
    private final PlayerMatchFactMapper mapper;

    @PersistenceContext
    private EntityManager entityManager;

    public PlayerMatchFactRepositoryAdapter(PlayerMatchFactJpaRepository jpaRepository, PlayerMatchFactMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public boolean saveIfAbsent(PlayerMatchFact fact) {
        if (fact == null) {
            return false;
        }
        if (jpaRepository.existsBySessionIdAndUserId(fact.getSessionId(), fact.getUserId().value())) {
            return false;
        }
        PlayerMatchFactJpaEntity entity = mapper.toEntity(fact);
        entityManager.persist(entity);
        entityManager.flush();
        return true;
    }
}