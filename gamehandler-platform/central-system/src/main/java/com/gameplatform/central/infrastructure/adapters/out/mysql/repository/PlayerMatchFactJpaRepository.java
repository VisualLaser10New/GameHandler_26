package com.gameplatform.central.infrastructure.adapters.out.mysql.repository;

import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactId;
import com.gameplatform.central.infrastructure.adapters.out.mysql.entity.PlayerMatchFactJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository JPA per l'accesso ai dati dei fatti partita dei giocatori.
 * <p>
 * La chiave primaria composita ({@code session_id}, {@code user_id}) &egrave;
 * rappresentata da {@link PlayerMatchFactId}. Il metodo di verifica di
 * esistenza viene utilizzato dall'adapter per rendere idempotente l'inserimento
 * del fatto partita, senza fare affidamento su violazioni di vincolo.
 * </p>
 *
 * @see PlayerMatchFactJpaEntity
 * @see PlayerMatchFactId
 */
@Repository
public interface PlayerMatchFactJpaRepository extends JpaRepository<PlayerMatchFactJpaEntity, PlayerMatchFactId> {

    /**
     * Verifica se esiste gi&agrave; un fatto partita per la coppia sessione-utente specificata.
     *
     * @param sessionId l'identificativo della sessione di gioco (non null)
     * @param userId    l'identificativo dell'utente (non null)
     * @return {@code true} se il fatto partita esiste gi&agrave;, {@code false} altrimenti
     */
    boolean existsBySessionIdAndUserId(String sessionId, String userId);
}