package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentMatchLocal;
import com.gameplatform.local.domain.ports.out.TournamentMatchLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentMatchLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentMatchLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import com.gameplatform.shared.domain.model.TournamentMatchId;
import com.gameplatform.shared.domain.model.TournamentMatchStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link TournamentMatchLocalRepository}.
 * Gestisce la persistenza degli incontri dei tornei, con operazioni
 * di upsert idempotenti per chiave primaria e funzionalità di
 * ricerca per partecipante e stato dell'incontro.
 *
 * @see TournamentMatchLocalRepository
 * @see TournamentMatchLocalJpaRepository
 */
@Component
public class TournamentMatchLocalRepositoryAdapter implements TournamentMatchLocalRepository {

    private final TournamentMatchLocalJpaRepository jpaRepository;
    private final TournamentMatchLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per gli incontri dei tornei
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public TournamentMatchLocalRepositoryAdapter(TournamentMatchLocalJpaRepository jpaRepository,
                                                 TournamentMatchLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un incontro del torneo nel database (upsert per chiave primaria).
     *
     * @param match l'incontro del torneo da salvare
     * @return l'incontro persistito, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public TournamentMatchLocal save(TournamentMatchLocal match) {
        if (match == null) {
            return null;
        }
        TournamentMatchLocalJpaEntity entity = mapper.toEntity(match);
        TournamentMatchLocalJpaEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    /**
     * Recupera un incontro del torneo tramite il suo identificativo.
     *
     * @param id l'identificativo dell'incontro
     * @return un {@code Optional} contenente l'incontro, vuoto se non trovato o se l'identificativo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<TournamentMatchLocal> findById(TournamentMatchId id) {
        if (id == null) {
            return Optional.empty();
        }
        return jpaRepository.findById(id.value()).map(mapper::toDomain);
    }

    /**
     * Recupera tutti gli incontri di un dato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return una lista di incontri per il torneo, vuota se il torneo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findByTournamentId(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        List<TournamentMatchLocalJpaEntity> entities = jpaRepository.findByTournamentId(tournamentId.value());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    /**
     * Recupera gli incontri programmati per un dato partecipante.
     *
     * @param userId l'identificativo dell'utente partecipante
     * @return una lista di incontri programmati per il partecipante, vuota se l'utente è nullo/vuoto
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentMatchLocal> findScheduledByParticipant(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        List<TournamentMatchLocalJpaEntity> entities =
                jpaRepository.findByParticipantAndStatus(userId, TournamentMatchStatus.SCHEDULED.name());
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(mapper::toDomain).collect(Collectors.toList());
    }

    /**
     * Elimina un incontro del torneo tramite il suo identificativo.
     *
     * @param id l'identificativo dell'incontro da eliminare; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteById(TournamentMatchId id) {
        if (id == null) {
            return;
        }
        jpaRepository.deleteById(id.value());
    }
}