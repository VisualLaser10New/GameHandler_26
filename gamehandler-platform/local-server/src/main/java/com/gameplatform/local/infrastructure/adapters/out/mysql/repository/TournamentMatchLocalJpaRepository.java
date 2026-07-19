package com.gameplatform.local.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.local.infrastructure.adapters.out.mysql.entity.TournamentMatchLocalJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Interfaccia Spring Data JPA per l'entità {@link TournamentMatchLocalJpaEntity}.
 * Gestisce gli incontri dei tornei, consentendo la ricerca per ID, per
 * torneo e per partecipante con stato, supportando sia partecipanti
 * individuali che membri di squadre.
 *
 * @see TournamentMatchLocalJpaEntity
 */
@Repository
public interface TournamentMatchLocalJpaRepository
        extends JpaRepository<TournamentMatchLocalJpaEntity, String> {

    /**
     * Recupera un incontro del torneo in base all'ID specificato.
     *
     * @param id l'identificativo univoco dell'incontro
     * @return un {@link Optional} contenente l'entità, oppure vuoto se non trovata
     */
    Optional<TournamentMatchLocalJpaEntity> findById(String id);

    /**
     * Recupera tutti gli incontri associati a un determinato torneo.
     *
     * @param tournamentId l'ID del torneo
     * @return una lista di entità {@link TournamentMatchLocalJpaEntity} per il torneo indicato
     */
    List<TournamentMatchLocalJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Recupera tutti gli incontri distinti di un torneo per un determinato
     * partecipante e stato. La ricerca considera sia i partecipanti diretti
     * (individuali) sia quelli indiretti tramite appartenenza a squadre,
     * verificando se l'utente è membro di una squadra partecipante all'incontro.
     *
     * @param userId l'ID dell'utente partecipante o membro di squadra
     * @param status lo stato dell'incontro da filtrare
     * @return una lista di entità {@link TournamentMatchLocalJpaEntity} distinte per partecipante e stato
     */
    @Query("SELECT DISTINCT m FROM TournamentMatchLocalJpaEntity m " +
           "WHERE m.status = :status " +
           "AND (m.participantA = :userId OR m.participantB = :userId " +
           "OR EXISTS (SELECT tm FROM TeamMemberLocalJpaEntity tm " +
           "WHERE tm.tournamentId = m.tournamentId " +
           "AND tm.userId = :userId " +
           "AND (tm.teamId = m.participantA OR tm.teamId = m.participantB)))")
    List<TournamentMatchLocalJpaEntity> findByParticipantAndStatus(
            @Param("userId") String userId,
            @Param("status") String status);
}