package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.PlayerMatchFact;

/**
 * Porta di persistenza per la tabella read-model {@code player_match_facts}.
 *
 * <p>Contiene una riga per ogni coppia (sessione, partecipante) e viene popolata
 * dalla proiezione {@code SyncEventProcessor} al consumo di un evento
 * {@code GAME_SESSION_COMPLETED}. La chiave primaria composta
 * {@code (session_id, user_id)} rende ogni fatto naturalmente idempotente.</p>
 *
 * @see PlayerMatchFact
 * @see #saveIfAbsent(PlayerMatchFact)
 */
public interface PlayerMatchFactRepository {

    /**
     * Inserisce il fatto di partita indicato se non esiste già alcun fatto per la
     * coppia {@code (sessionId, userId)}.
     *
     * @param fact il fatto di partita da inserire; non deve essere {@code null}
     * @return {@code true} se la riga è stata inserita come nuova; {@code false} se esisteva già un fatto per la
     *         stessa coppia {@code (sessionId, userId)} e l'operazione è stata un no-op idempotente
     * @throws IllegalArgumentException se {@code fact} è {@code null}
     * @see #saveIfAbsent(PlayerMatchFact)
     */
    boolean saveIfAbsent(PlayerMatchFact fact);
}
