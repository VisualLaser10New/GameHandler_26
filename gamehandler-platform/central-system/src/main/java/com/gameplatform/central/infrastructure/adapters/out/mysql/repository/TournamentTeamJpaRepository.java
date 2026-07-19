package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.TournamentTeamJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository JPA per l'accesso ai dati delle squadre partecipanti ai tornei.
 * <p>
 * Fornisce metodi di interrogazione e modifica per le entit&agrave; squadra,
 * inclusa la ricerca per torneo, la verifica di unicit&agrave; del nome e
 * l'eliminazione fisica tramite query JPQL personalizzata.
 * </p>
 *
 * @see TournamentTeamJpaEntity
 * @see TournamentTeamMemberJpaRepository
 */
@Repository
public interface TournamentTeamJpaRepository extends JpaRepository<TournamentTeamJpaEntity, String> {

    /**
     * Restituisce la squadra associata all'identificativo specificato, se presente.
     *
     * @param id l'identificativo univoco della squadra (non null)
     * @return un {@code Optional} contenente la squadra se trovata, vuoto altrimenti
     */
    Optional<TournamentTeamJpaEntity> findById(String id);

    /**
     * Restituisce tutte le squadre appartenenti al torneo specificato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @return una lista di squadre del torneo, vuota se il torneo non ha squadre o non esiste
     */
    List<TournamentTeamJpaEntity> findByTournamentId(String tournamentId);

    /**
     * Restituisce la squadra associata al torneo e al nome specificati, se presente.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @param name         il nome della squadra (non null)
     * @return un {@code Optional} contenente la squadra se trovata, vuoto altrimenti
     */
    Optional<TournamentTeamJpaEntity> findByTournamentIdAndName(String tournamentId, String name);

    /**
     * Verifica se esiste gi&agrave; una squadra con il nome specificato all'interno del torneo indicato.
     *
     * @param tournamentId l'identificativo del torneo (non null)
     * @param name         il nome della squadra da verificare (non null)
     * @return {@code true} se esiste una squadra con lo stesso nome nel torneo, {@code false} altrimenti
     */
    boolean existsByTournamentIdAndName(String tournamentId, String name);

    /**
     * Elimina fisicamente la squadra associata all'identificativo specificato.
     *
     * @param id l'identificativo univoco della squadra da eliminare (non null)
     */
    @Modifying
    @Query("delete from TournamentTeamJpaEntity t where t.id = :id")
    void deleteById(@Param("id") String id);
}