package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentMatch;
import com.gameplatform.central.domain.ports.out.TournamentMatchRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentMatchJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentMatchMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentMatchJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentMatchRepository} port (FASE 4 / C.8
 * scaffolding). Mirrors the {@code GameDefinitionRepositoryAdapter} /
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; writes carry the default {@code @Transactional}
 * propagation and reads are marked {@code @Transactional(readOnly = true)}.
 * All read paths are null-safe, returning {@code Optional.empty()} /
 * {@code List.of()} when their arguments are {@code null}.
 */
@Component
public class TournamentMatchRepositoryAdapter implements TournamentMatchRepository {

    private final TournamentMatchJpaRepository jpaRepo;
    private final TournamentMatchMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei match di torneo.
     *
     * @param jpaRepo repository JPA per la gestione delle entit&agrave; di match
     * @param mapper  mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public TournamentMatchRepositoryAdapter(TournamentMatchJpaRepository jpaRepo,
                                            TournamentMatchMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) un match di torneo e restituisce l'entit&agrave; persistita.
     *
     * @param match il match da persistere; non deve essere {@code null}
     * @return il match salvato, con eventuali valorizzazioni gestite dal database
     * @see TournamentMatchJpaRepository#save
     */
    @Override
    @Transactional
    public TournamentMatch save(TournamentMatch match) {
        TournamentMatchJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(match));
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce il match di torneo identificato dal relativo identificativo.
     *
     * @param id l'identificativo del match; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente il match trovato, o vuoto se assente o se {@code id} &egrave; {@code null}
     * @see TournamentMatchJpaRepository#findById
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentMatch> findById(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco dei match associati a un torneo.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista dei match del torneo; lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentMatchJpaRepository#findByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatch> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentMatchJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    /**
     * Elimina il match identificato dal relativo identificativo.
     *
     * @param id l'identificativo del match da eliminare; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentMatchJpaRepository#deleteById
     */
    @Override
    @Transactional
    public void deleteById(TournamentMatchId id) {
        if (id == null) {
            return;
        }
        jpaRepo.deleteById(id.value());
    }

    /**
     * Restituisce il match identificato acquisendone il lock pessimistico in scrittura.
     *
     * @param id l'identificativo del match; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente il match trovato (bloccato), o vuoto se assente o se {@code id} &egrave; {@code null}
     * @see TournamentMatchJpaRepository#findByIdForUpdate
     */
    @Override
    @Transactional
    public Optional<TournamentMatch> findByIdForUpdate(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }

    /**
     * Restituisce il match di un torneo individuato da turno e posizione nel bracket, acquisendone il lock pessimistico.
     *
     * @param tournamentId    l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @param round           il numero del turno del match
     * @param bracketPosition la posizione del match all'interno del bracket
     * @return l'{@link Optional} contenente il match trovato (bloccato), o vuoto se assente o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentMatchJpaRepository#findByTournamentIdAndRoundAndBracketPositionForUpdate
     */
    @Override
    @Transactional
    public Optional<TournamentMatch> findByTournamentIdAndRoundAndBracketPositionForUpdate(
            TournamentId tournamentId, int round, int bracketPosition) {
        if (tournamentId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndRoundAndBracketPositionForUpdate(
                tournamentId.value(), round, bracketPosition).map(mapper::toDomain);
    }
}