package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentStanding;
import com.gameplatform.central.domain.ports.out.TournamentStandingRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentStandingJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentStandingMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentStandingJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentStandingRepository} port (FASE 4 / C.8
 * scaffolding). Mirrors the {@code GameDefinitionRepositoryAdapter} /
 * {@code LocalAdminBuildingRepositoryAdapter} shape: constructor-injects the
 * JPA repository + mapper; writes carry the default {@code @Transactional}
 * propagation and reads are marked {@code @Transactional(readOnly = true)}.
 * All read paths are null-safe, returning {@code Optional.empty()} /
 * {@code List.of()} when their arguments are {@code null}.
 */
@Component
public class TournamentStandingRepositoryAdapter implements TournamentStandingRepository {

    private final TournamentStandingJpaRepository jpaRepo;
    private final TournamentStandingMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper delle classifiche di torneo.
     *
     * @param jpaRepo repository JPA per la gestione delle entit&agrave; di classifica
     * @param mapper  mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public TournamentStandingRepositoryAdapter(TournamentStandingJpaRepository jpaRepo,
                                               TournamentStandingMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) una classifica di torneo e restituisce l'entit&agrave; persistita.
     *
     * @param standing la classifica da persistere; non deve essere {@code null}
     * @return la classifica salvata, con eventuali valorizzazioni gestite dal database
     * @see TournamentStandingJpaRepository#save
     */
    @Override
    @Transactional
    public TournamentStanding save(TournamentStanding standing) {
        TournamentStandingJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(standing));
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce la classifica di un torneo per un partecipante.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @param participantId l'identificativo del partecipante; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente la classifica trovata, o vuoto se assente o se un argomento &egrave; {@code null}
     * @see TournamentStandingJpaRepository#findByTournamentIdAndParticipantId
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentStanding> findByTournamentAndParticipantId(TournamentId tournamentId,
                                                                       String participantId) {
        if (tournamentId == null || participantId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndParticipantId(tournamentId.value(), participantId)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco delle classifiche associate a un torneo.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista delle classifiche del torneo; lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentStandingJpaRepository#findByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentStanding> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentStandingJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    /**
     * Elimina la classifica di un torneo per un partecipante.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} il metodo non effettua alcuna operazione
     * @param participantId l'identificativo del partecipante; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentStandingJpaRepository#deleteByTournamentAndParticipantId
     */
    @Override
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return;
        }
        jpaRepo.deleteByTournamentAndParticipantId(tournamentId.value(), participantId);
    }

    /**
     * Restituisce l'elenco delle classifiche di un torneo acquisendone il lock pessimistico in scrittura.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista delle classifiche del torneo (bloccate); lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentStandingJpaRepository#findByTournamentIdForUpdate
     */
    @Override
    @Transactional
    public List<TournamentStanding> findByTournamentIdForUpdate(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentStandingJpaEntity> entities =
                jpaRepo.findByTournamentIdForUpdate(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }
}