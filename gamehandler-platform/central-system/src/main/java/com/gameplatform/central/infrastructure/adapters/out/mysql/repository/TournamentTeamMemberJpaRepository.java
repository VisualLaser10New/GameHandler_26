package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamMemberJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository JPA per l'accesso ai dati dei membri delle squadre dei tornei.
 * <p>
 * Fornisce metodi per interrogare i membri di una squadra, eliminare tutti i
 * membri di una squadra e recuperare gli identificativi delle squadre a cui
 * appartiene un determinato utente.
 * </p>
 *
 * @see TournamentTeamMemberJpaEntity
 * @see TournamentTeamMemberId
 * @see TournamentTeamJpaRepository
 */
@Repository
public interface TournamentTeamMemberJpaRepository extends JpaRepository<TournamentTeamMemberJpaEntity, TournamentTeamMemberId> {

    /**
     * Restituisce tutti i membri appartenenti alla squadra specificata.
     *
     * @param teamId l'identificativo della squadra (non null)
     * @return una lista di membri della squadra, vuota se la squadra non ha membri o non esiste
     */
    List<TournamentTeamMemberJpaEntity> findByTeamId(String teamId);

    /**
     * Elimina tutti i membri della squadra specificata.
     *
     * @param teamId l'identificativo della squadra di cui rimuovere tutti i membri (non null)
     */
    @Modifying
    @Query("delete from TournamentTeamMemberJpaEntity m where m.teamId = :teamId")
    void deleteByTeamId(@Param("teamId") String teamId);

    /**
     * Restituisce gli identificativi di tutte le squadre a cui appartiene l'utente specificato.
     *
     * @param userId l'identificativo dell'utente (non null)
     * @return una lista di identificativi di squadre, vuota se l'utente non &egrave; membro di alcuna squadra
     */
    @Query("select m.teamId from TournamentTeamMemberJpaEntity m where m.userId = :userId")
    List<String> findTeamIdsByUserId(@Param("userId") String userId);
}