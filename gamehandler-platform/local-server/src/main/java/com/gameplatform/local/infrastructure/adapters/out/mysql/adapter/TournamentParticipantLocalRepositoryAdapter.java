package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.model.TournamentParticipantLocal;
import com.gameplatform.local.domain.ports.out.TournamentParticipantsLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentParticipantLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.mapper.TournamentParticipantLocalMapper;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TournamentParticipantLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter JPA per il port {@link TournamentParticipantsLocalRepository}.
 * Gestisce la persistenza dei partecipanti ai tornei, con operazioni
 * di upsert per chiave primaria composta (tournamentId, participantId)
 * e funzionalità di eliminazione bulk per sostituzione completa dello
 * snapshot di partecipanti per torneo.
 *
 * @see TournamentParticipantsLocalRepository
 * @see TournamentParticipantLocalJpaRepository
 */
@Component
public class TournamentParticipantLocalRepositoryAdapter implements TournamentParticipantsLocalRepository {

    private final TournamentParticipantLocalJpaRepository jpaRepository;
    private final TournamentParticipantLocalMapper mapper;

    /**
     * Costruisce un nuovo adapter con le dipendenze necessarie.
     *
     * @param jpaRepository repository JPA per i partecipanti ai tornei
     * @param mapper        mapper per la conversione tra entity e dominio
     */
    public TournamentParticipantLocalRepositoryAdapter(TournamentParticipantLocalJpaRepository jpaRepository,
                                                        TournamentParticipantLocalMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    /**
     * Salva un partecipante al torneo nel database (upsert per chiave composta).
     *
     * @param participant il partecipante da salvare
     * @return il partecipante persistito, {@code null} se l'argomento è {@code null}
     */
    @Override
    @Transactional
    public TournamentParticipantLocal save(TournamentParticipantLocal participant) {
        if (participant == null) {
            return null;
        }
        TournamentParticipantLocalJpaEntity entity = mapper.toEntity(participant);
        TournamentParticipantLocalJpaEntity saved = jpaRepository.save(entity);
        return mapper.toDomain(saved);
    }

    /**
     * Recupera tutti i partecipanti di un dato torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @return una lista di partecipanti, vuota se il torneo è {@code null}
     */
    @Override
    @Transactional(readOnly = true)
    public List<TournamentParticipantLocal> findByTournament(TournamentId tournamentId) {
        if (tournamentId == null) {
            return List.of();
        }
        return jpaRepository.findByTournamentId(tournamentId.value()).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * Elimina tutti i partecipanti di un torneo in un'unica operazione bulk.
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
     * Elimina un partecipante specifico da un torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @param participantId l'identificativo del partecipante da eliminare; se uno dei parametri è nullo/vuoto l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void deleteByTournamentAndParticipantId(TournamentId tournamentId, String participantId) {
        if (tournamentId == null || participantId == null || participantId.isBlank()) {
            return;
        }
        jpaRepository.deleteByTournamentIdAndParticipantId(tournamentId.value(), participantId);
    }
}