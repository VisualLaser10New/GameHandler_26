package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.ports.out.TournamentBuildingRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentBuildingJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentBuildingMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentBuildingJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JPA adapter for the {@link TournamentBuildingRepository} port. Mirrors the
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the JPA
 * repository + mapper and exposes the primitive {@code String} building-id API
 * (the backing {@code tournament_buildings} table is a pure join-table with no
 * central domain POJO of its own, per FASE 4 PIANO &sect;3.1). Writes carry the
 * default {@code @Transactional} propagation; reads are marked
 * {@code @Transactional(readOnly = true)} and are null-safe
 * ({@code List.of()} / {@code false} on {@code null} args).
 */
@Component
public class TournamentBuildingRepositoryAdapter implements TournamentBuildingRepository {

    private final TournamentBuildingJpaRepository jpaRepo;
    private final TournamentBuildingMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei legami torneo-edificio.
     *
     * @param jpaRepo repository JPA per la gestione delle entit&agrave; di legame
     * @param mapper  mapper che converte tra i parametri e l'entit&agrave; JPA
     */
    public TournamentBuildingRepositoryAdapter(TournamentBuildingJpaRepository jpaRepo,
                                              TournamentBuildingMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    /**
     * Associa tutti gli edifici indicati al torneo, ignorando i valori nulli o vuoti.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} il metodo non effettua alcuna operazione
     * @param buildingIds  l'elenco degli identificativi edificio da associare; se {@code null} il metodo non effettua alcuna operazione
     */
    @Override
    @Transactional
    public void saveAll(TournamentId tournamentId, List<String> buildingIds) {
        if (tournamentId == null || buildingIds == null) {
            return;
        }
        for (String buildingId : buildingIds) {
            if (buildingId == null || buildingId.isBlank()) {
                continue;
            }
            jpaRepo.save(mapper.toEntity(tournamentId.value(), buildingId));
        }
    }

    /**
     * Restituisce l'elenco degli identificativi edificio associati a un torneo.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista degli identificativi edificio; lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentBuildingJpaRepository#findByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public List<String> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentBuildingJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(TournamentBuildingJpaEntity::getBuildingId).toList();
    }

    /**
     * Elimina tutti i legami tra il torneo indicato e gli edifici associati.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentBuildingJpaRepository#deleteByTournamentId
     */
    @Override
    @Transactional
    public void deleteByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepo.deleteByTournamentId(tournamentId.value());
    }

    /**
     * Verifica l'esistenza di un legame tra un torneo e un edificio.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce {@code false}
     * @param buildingId   l'identificativo dell'edificio; se {@code null} restituisce {@code false}
     * @return {@code true} se esiste il legame, {@code false} altrimenti
     * @see TournamentBuildingJpaRepository#existsByTournamentIdAndBuildingId
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndBuilding(TournamentId tournamentId, String buildingId) {
        if (tournamentId == null || buildingId == null) {
            return false;
        }
        return jpaRepo.existsByTournamentIdAndBuildingId(tournamentId.value(), buildingId);
    }
}