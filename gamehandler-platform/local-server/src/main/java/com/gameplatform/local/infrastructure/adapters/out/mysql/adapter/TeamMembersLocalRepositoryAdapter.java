package com.gameplatform.local.infrastructure.adapters.out.mysql.adapter;

import com.gameplatform.local.domain.ports.out.TeamMembersLocalRepository;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalJpaEntity;
import com.gameplatform.local.infrastructure.adapters.out.mysql.repository.TeamMemberLocalJpaRepository;
import com.gameplatform.shared.domain.model.TournamentId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter JPA per il port {@link TeamMembersLocalRepository}.
 * Gestisce la persistenza delle appartenenze dei membri ai team
 * nei tornei, utilizzando una tabella di join a tre campi
 * (tournamentId, teamId, userId) senza modello di dominio intermedio.
 * Le operazioni di salvataggio sono upsert per chiave composta e
 * l'eliminazione per torneo rimuove l'intero snapshot in un'unica
 * operazione bulk.
 *
 * @see TeamMembersLocalRepository
 * @see TeamMemberLocalJpaRepository
 */
@Component
public class TeamMembersLocalRepositoryAdapter implements TeamMembersLocalRepository {

    private final TeamMemberLocalJpaRepository jpaRepository;

    /**
     * Costruisce un nuovo adapter con il repository JPA necessario.
     *
     * @param jpaRepository repository JPA per i membri del team
     */
    public TeamMembersLocalRepositoryAdapter(TeamMemberLocalJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * Salva un'appartenenza di un membro a un team per un torneo.
     *
     * @param tournamentId l'identificativo del torneo
     * @param teamId       l'identificativo del team
     * @param userId       l'identificativo dell'utente; se uno dei parametri è nullo o vuoto l'operazione non viene eseguita
     */
    @Override
    @Transactional
    public void save(String tournamentId, String teamId, String userId) {
        if (tournamentId == null || tournamentId.isBlank()
                || teamId == null || teamId.isBlank()
                || userId == null || userId.isBlank()) {
            return;
        }
        jpaRepository.save(new TeamMemberLocalJpaEntity(tournamentId, teamId, userId));
    }

    /**
     * Elimina tutte le appartenenze ai team per un dato torneo in un'unica operazione bulk.
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
}