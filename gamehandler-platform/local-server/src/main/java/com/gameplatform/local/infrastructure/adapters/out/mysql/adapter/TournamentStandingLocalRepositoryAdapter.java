package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentStandingLocal;
import com.gameplatform.local.domain.ports.out.TournamentStandingsLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentStandingLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentStandingLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentStandingLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link TournamentStandingsLocalRepository}.
 * Gestisce la persistenza delle classifiche dei tornei, con operazioni
 * di upsert per chiave primaria composta (tournamentId, participantId)
 * e funzionalità di eliminazione bulk per sostituzione completa dello
 * snapshot delle classifiche per torneo.
 *
 * @see TournamentStandingsLocalRepository
 * @see TournamentStandingLocalJpaRepository
 */
@Component
public class TournamentStandingLocalRepositoryAdapter implements TournamentStandingsLocalRepository {

    private final TournamentStandingLocalJpaRepository jpaRepository;
    private final TournamentStandingLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per le classifiche dei tornei
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public TournamentStandingLocalRepositoryAdapter(TournamentStandingLocalJpaRepository jpaRepository,
                                                     TournamentStandingLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva una classifica di torneo nel database (upsert per chiave composta).
     *
     * @param standing la classifica da salvare
     * @return la classifica persistita, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public TournamentStandingLocal save(TournamentStandingLocal standing) {
        if (standing == null) {
            return null;
        }
        TournamentStandingLocalJpaEntity entity = mapper.toEntity(standing);
        TournamentStandingLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera tutte le classifiche di un dato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return una lista di classifiche per il torneo, vuota se il torneo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentStandingLocal> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        return jpaRepository.findByTournamentId(tournamentId.value()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Elimina tutte le classifiche di un torneo in un'unica operazione bulk.
     *
     * @param tournamentId l'identificativo del torneo; se {@code null} l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return;
        }
        jpaRepository.deleteByTournamentId(tournamentId.value());
    }

    /**
     * Verifica se esiste una classifica per un dato torneo e partecipante.
     *
     * @param tournamentId l'identificativo del torneo
     * @param participantId l'identificativo del partecipante
     * @return {@code true} se la classifica esiste, {@code false} altrimenti o se uno dei parametri è nullo/vuoto
     */
    @Override
    @Transactional(readOnly = true)
    public boolean existsByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null || participantId.isBlank()) {
            return false;
        }
        return jpaRepository.existsByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}