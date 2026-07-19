package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalId;
import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TeamMemberLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Interfaccia Spring Data JPA per l'entità {@link TeamMemberLocalJpaEntity}.
 * La chiave primaria composita è {@link TeamMemberLocalId}; il metodo
 * {@code save} predefinito esegue un upsert basato sulla chiave composita
 * ({@code tournamentId}, {@code teamId}, {@code userId}). Il servizio di
 * sincronizzazione utilizza {@link #deleteByTournamentId} per la
 * sostituzione completa dello snapshot (delete+insert con idempotenza per
 * {@code tournamentId}).
 *
 * @see TeamMemberLocalJpaEntity
 * @see TeamMemberLocalId
 */
@Repository
public interface TeamMemberLocalJpaRepository
        extends JpaRepository<TeamMemberLocalJpaEntity, TeamMemberLocalId> {

    /**
     * Recupera tutti i membri delle squadre associati a un determinato torneo.
     *
     * @param tournamentId l'ID del torneo
     * @return una lista di entità {@link TeamMemberLocalJpaEntity} per il torneo indicato
     */
    List<TeamMemberLocalJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Elimina tutti i membri delle squadre associati al torneo specificato.
     * Utilizzato dal servizio di sincronizzazione per la sostituzione
     * completa dello snapshot.
     *
     * @param tournamentId l'ID del torneo di cui rimuovere i membri
     */
    void deleteByTournamentId(String tournamentId);
}