package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.ports.out.TournamentRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape: constructor-injects the JPA repository + mapper; writes carry the default
 * {@code @Transactional} propagation and reads are marked
 * {@code @Transactional(readOnly = true)}. All read paths are null-safe, returning
 * {@code Optional.empty()} / {@code List.of()} / {@code false} when their arguments
 * are {@code null}.
 */
@Component
public class TournamentRepositoryAdapter implements TournamentRepository {

    private final TournamentJpaRepository jpaRepo;
    private final TournamentMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei tornei.
     *
     * @param jpaRepo repository JPA per la gestione delle entit&agrave; di torneo
     * @param mapper  mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public TournamentRepositoryAdapter(TournamentJpaRepository jpaRepo, TournamentMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) un torneo e restituisce l'entit&agrave; persistita.
     *
     * @param tournament il torneo da persistere; non deve essere {@code null}
     * @return il torneo salvato, con eventuali valorizzazioni gestite dal database
     * @see TournamentJpaRepository#save
     */
    @Override
    @Transactional
    public Tournament save(Tournament tournament) {
        TournamentJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(tournament));
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce il torneo identificato dal relativo identificativo.
     *
     * @param id l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente il torneo trovato, o vuoto se assente o se {@code id} &egrave; {@code null}
     * @see TournamentJpaRepository#findById
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Tournament> findById(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco di tutti i tornei ordinati per data di creazione decrescente.
     *
     * @return la lista di tutti i tornei; lista vuota se non ve ne sono
     * @see TournamentJpaRepository#findAllByOrderByCreatedAtDesc
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tournament> findAll() {
        List<TournamentJpaEntity> entities = jpaRepo.findAllByOrderByCreatedAtDesc();
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    /**
     * Restituisce l'elenco dei tornei che si trovano nello stato indicato.
     *
     * @param status lo stato dei tornei da cercare; se {@code null} restituisce una lista vuota
     * @return la lista dei tornei nello stato indicato; lista vuota se non ve ne sono o se {@code status} &egrave; {@code null}
     * @see TournamentJpaRepository#findByStatus
     */
    @Override
    @Transactional(readOnly = true)
    public List<Tournament> findByStatus(TournamentStatus status) {
        if (status == null) {
            return List.of();
        }
        List<TournamentJpaEntity> entities = jpaRepo.findByStatus(status.name());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    /**
     * Verifica l'esistenza di un torneo identificato dal relativo identificativo.
     *
     * @param id l'identificativo del torneo; se {@code null} restituisce {@code false}
     * @return {@code true} se il torneo esiste, {@code false} altrimenti
     * @see TournamentJpaRepository#existsById
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsById(TournamentId id) {
        if (id == null) {
            return false;
        }
        return jpaRepo.existsById(id.value());
    }

    /**
     * Elimina il torneo identificato dal relativo identificativo.
     *
     * @param id l'identificativo del torneo da eliminare; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentJpaRepository#deleteById
     */
    @Override
    @Transactional
    public void deleteById(TournamentId id) {
        if (id == null) {
            return;
        }
        jpaRepo.deleteById(id.value());
    }

    /**
     * Restituisce il torneo identificato acquisendone il lock pessimistico in scrittura.
     *
     * @param id l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente il torneo trovato (bloccato), o vuoto se assente o se {@code id} &egrave; {@code null}
     * @see TournamentJpaRepository#findByIdForUpdate
     */
    @Override
    @Transactional
    public Optional<Tournament> findByIdForUpdate(TournamentId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepo.findByIdForUpdate(id.value()).map(mapper::toDomain);
    }
}