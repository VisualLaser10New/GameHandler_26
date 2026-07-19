package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.LocalAdminBuilding;
import com.gameplatform.local.domain.ports.out.LocalAdminBuildingLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.LocalAdminBuildingMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.LocalAdminBuildingJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link LocalAdminBuildingLocalRepository}.
 * Gestisce la persistenza delle associazioni tra amministratori locali
 * e edifici, con operazioni di upsert per chiave primaria composta
 * (user_id, building_id) che garantiscono idempotenza in caso di
 * riapplicazione dello stesso evento di assegnazione.
 *
 * @see LocalAdminBuildingLocalRepository
 * @see LocalAdminBuildingJpaRepository
 */
@Component
public class LocalAdminBuildingLocalRepositoryAdapter implements LocalAdminBuildingLocalRepository {

    private final LocalAdminBuildingJpaRepository jpaRepository;
    private final LocalAdminBuildingMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le associazioni amministratore-edificio
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public LocalAdminBuildingLocalRepositoryAdapter(LocalAdminBuildingJpaRepository jpaRepository,
                                                     LocalAdminBuildingMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un'associazione amministratore-edificio nel database (upsert per chiave composta).
     *
     * @param binding l'associazione da salvare
     * @return l'associazione persistita
     */
    @Override
    @Transactional
    public LocalAdminBuilding save(LocalAdminBuilding binding) {
        LocalAdminBuildingJpaEntity entity = mapper.toEntity(binding);
        LocalAdminBuildingJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Verifica se esiste un'associazione tra un utente e un edificio.
     *
     * @param userId      l'identificativo dell'utente
     * @param buildingId  l'identificativo dell'edificio
     * @return {@code true} se l'associazione esiste, {@code false} altrimenti o se uno dei parametri è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByUserIdAndBuildingId(UserId userId, BuildingId buildingId) {
        if (userId == null || buildingId == null) {
            return false;
        }
        return jpaRepository.existsByUserIdAndBuildingId(userId.value(), buildingId.id());
    }

    /**
     * Elimina un'associazione amministratore-edificio.
     *
     * @param userId      l'identificativo dell'utente
     * @param buildingId  l'identificativo dell'edificio; se uno dei parametri è {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteByUserIdAndBuildingId(UserId userId, BuildingId buildingId) {
        if (userId == null || buildingId == null) {
            return;
        }
        jpaRepository.deleteByUserIdAndBuildingId(userId.value(), buildingId.id());
    }

    /**
     * Recupera tutte le associazioni edificio per un dato utente.
     *
     * @param userId l'identificativo dell'utente
     * @return una lista di associazioni per l'utente, vuota se l'utente è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<LocalAdminBuilding> findByUserId(UserId userId) {
        if (userId == null) {
            return List.of();
        }
        return jpaRepository.findByUserId(userId.value()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }
}