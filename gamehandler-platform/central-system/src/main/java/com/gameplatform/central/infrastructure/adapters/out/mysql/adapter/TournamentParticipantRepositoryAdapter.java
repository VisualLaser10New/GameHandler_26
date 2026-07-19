package com.gameplatform.central.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.central.domain.model.TournamentParticipant;
import com.gameplatform.central.domain.ports.out.TournamentParticipantRepository;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentParticipantJpaEntity;
import com.gameplatform.central.infrastructure.adapters.out.mysql.mapper.TournamentParticipantMapper;
import com.gameplatform.central.infrastructure.adapters.out.mysql.repository.TournamentParticipantJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link TournamentParticipantRepository} port. Mirrors the
 * {@code GameDefinitionRepositoryAdapter} / {@code LocalAdminBuildingRepositoryAdapter}
 * shape: constructor-injects the JPA repository + mapper; writes carry the default
 * {@code @Transactional} propagation and reads are marked
 * {@code @Transactional(readOnly = true)}. All read paths are null-safe, returning
 * {@code Optional.empty()} / {@code List.of()} / {@code false} / {@code 0L} when
 * their arguments are {@code null}.
 */
@Component
public class TournamentParticipantRepositoryAdapter implements TournamentParticipantRepository {

    private final TournamentParticipantJpaRepository jpaRepo;
    private final TournamentParticipantMapper mapper;

    /**
     * Costruisce l'adapter iniettando il repository JPA e il mapper dei partecipanti di torneo.
     *
     * @param jpaRepo repository JPA per la gestione delle entit&agrave; di partecipante
     * @param mapper  mapper che converte tra il modello di dominio e l'entit&agrave; JPA
     */
    public TournamentParticipantRepositoryAdapter(TournamentParticipantJpaRepository jpaRepo,
                                                  TournamentParticipantMapper mapper) {
        this.jpaRepo = jpaRepo;
        this.mapper = mapper;
    }

    /**
     * Salva (o aggiorna) un partecipante di torneo e restituisce l'entit&agrave; persistita.
     *
     * @param participant il partecipante da persistere; non deve essere {@code null}
     * @return il partecipante salvato, con eventuali valorizzazioni gestite dal database
     * @see TournamentParticipantJpaRepository#save
     */
    @Override
    @Transactional
    public TournamentParticipant save(TournamentParticipant participant) {
        TournamentParticipantJpaEntity savedEntity = jpaRepo.save(mapper.toEntity(participant));
        return mapper.toDomain(savedEntity);
    }

    /**
     * Restituisce il partecipante di un torneo identificato dal relativo identificativo.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} restituisce {@link Optional#empty()}
     * @param participantId l'identificativo del partecipante; se {@code null} restituisce {@link Optional#empty()}
     * @return l'{@link Optional} contenente il partecipante trovato, o vuoto se assente o se un argomento &egrave; {@code null}
     * @see TournamentParticipantJpaRepository#findByTournamentIdAndParticipantId
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentParticipant> findByTournamentAndParticipantId(TournamentId tournamentId,
                                                                           String participantId) {
        if (tournamentId == null || participantId == null) {
            return Optional.empty();
        }
        return jpaRepo.findByTournamentIdAndParticipantId(tournamentId.value(), participantId)
                .map(mapper::toDomain);
    }

    /**
     * Restituisce l'elenco dei partecipanti associati a un torneo.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce una lista vuota
     * @return la lista dei partecipanti del torneo; lista vuota se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentParticipantJpaRepository#findByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentParticipant> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentParticipantJpaEntity> entities = jpaRepo.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).toList();
    }

    /**
     * Restituisce il numero di partecipanti associati a un torneo.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} restituisce {@code 0L}
     * @return il numero di partecipanti del torneo; {@code 0L} se non ve ne sono o se {@code tournamentId} &egrave; {@code null}
     * @see TournamentParticipantJpaRepository#countByTournamentId
     */
    @Override
    @Transactional(readOnly = true)
    public long countByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return 0L;
        }
        return jpaRepo.countByTournamentId(tournamentId.value());
    }

    /**
     * Verifica l'esistenza di un partecipante in un torneo.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} restituisce {@code false}
     * @param participantId l'identificativo del partecipante; se {@code null} restituisce {@code false}
     * @return {@code true} se il partecipante esiste, {@code false} altrimenti
     * @see TournamentParticipantJpaRepository#existsByTournamentIdAndParticipantId
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return false;
        }
        return jpaRepo.existsByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }

    /**
     * Elimina il legame tra un torneo e un partecipante.
     *
     * @param tournamentId  l'identificativo del torneo; se {@code null} il metodo non effettua alcuna operazione
     * @param participantId l'identificativo del partecipante; se {@code null} il metodo non effettua alcuna operazione
     * @see TournamentParticipantJpaRepository#deleteByTournamentIdAndParticipantId
     */
    @Override
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null) {
            return;
        }
        jpaRepo.deleteByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}