package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.LocalAdminBuilding;
import com.gameplatform.central.domain.ports.out.LocalAdminBuildingRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.LocalAdminBuildingJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.LocalAdminBuildingMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.LocalAdminBuildingJpaRepository;
import com.gameplatform.shared.domain.model.BuildingId;
import com.gameplatform.shared.domain.model.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * JPA adapter for the {@link LocalAdminBuildingRepository} port. Matches the
 * {@code UserRepositoryAdapter} / {@code OutboxEventRepositoryAdapter} shape:
 * constructor-injects the JPA repository + mapper; {@code save} is an upsert by
 * composite PK (the underlying {@link LocalAdminBuildingJpaRepository#save}
 * merges an existing row if the (user_id, building_id) PK is already present).
 */
@Component
public class LocalAdminBuildingRepositoryAdapter implements LocalAdminBuildingRepository {

    private final LocalAdminBuildingJpaRepository jpaRepository;
    private final LocalAdminBuildingMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei legami tra amministratore locale e edificio.
     *
     * @param jpaRepository repository JPA per la gestione delle entit&agrave; di legame
     * @param mapper        mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public LocalAdminBuildingRepositoryAdapter(LocalAdminBuildingJpaRepository jpaRepository,
                                              LocalAdminBuildingMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) un legame tra amministratore locale ed edificio e restituisce l'entit&agrave; persistita.
     *
     * @param binding il legame da persistere; non deve essere {@code null}
     * @return il legame salvato, con eventuali valorizzazioni gestite dal database
     * @see LocalAdminBuildingJpaRepository#save
     */
    @Override
    @Transactional
    public LocalAdminBuilding save(LocalAdminBuilding binding) {
        LocalAdminBuildingJpaEntity entity = mapper.toEntity(binding);
        LocalAdminBuildingJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Verifica l'esistenza di un legame tra un utente e un edificio.
     *
     * @param userId     l'identificativo dell'utente; se {@code null} restituisce {@code false}
     * @param buildingId l'identificativo dell'edificio; se {@code null} restituisce {@code false}
     * @return {@code true} se esiste il legame, {@code false} altrimenti
     * @see LocalAdminBuildingJpaRepository#existsByUserIdAndBuildingId
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
     * Elimina il legame tra un utente e un edificio.
     *
     * @param userId     l'identificativo dell'utente; se {@code null} il metodo non effettua alcuna operazione
     * @param buildingId l'identificativo dell'edificio; se {@code null} il metodo non effettua alcuna operazione
     * @see LocalAdminBuildingJpaRepository#deleteByUserIdAndBuildingId
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
     * Restituisce l'elenco dei legami associati a un utente.
     *
     * @param userId l'identificativo dell'utente; se {@code null} restituisce una lista vuota
     * @return la lista dei legami dell'utente; lista vuota se non ve ne sono o se {@code userId} &egrave; {@code null}
     * @see LocalAdminBuildingJpaRepository#findByUserId
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