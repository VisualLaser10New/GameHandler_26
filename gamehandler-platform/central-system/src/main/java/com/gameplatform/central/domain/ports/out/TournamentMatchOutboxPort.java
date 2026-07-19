package com.gameplatform.central.domain.ports.out;

import com.gameplatform.central.domain.model.Tournament;
import com.gameplatform.central.domain.model.TournamentMatch;

/**
 * Contratto di emissione outbox per una partita di torneo appena programmata.
 *
 * <p>L'adattatore infrastrutturale ({@code TournamentMatchOutboxAdapter}) è
 * responsabile di costruire il {@code TournamentMatchScheduledDto} con un nuovo
 * UUID, serializzarlo in JSON e persistere la riga {@code OutboxEvent} di tipo
 * {@code "TOURNAMENT_MATCH_SCHEDULED"} all'interno della transazione attiva del
 * chiamante (pattern Outbox). La riga resta {@code PENDING} finché lo scheduler
 * non la svuota e la invia ai server locali interessati.</p>
 *
 * <p>Questa porta dipende deliberatamente solo da tipi di dominio
 * ({@link TournamentMatch}, {@link Tournament}) e non da {@code shared-dto} o
 * {@code OutboxEvent}, mantenendo il livello di dominio libero da infrastruttura
 * e DTO. L'adattatore possiede la costruzione del DTO e la generazione dell'UUID.</p>
 *
 * <p>Le implementazioni NON DEVONO mai essere invocate per righe {@code BYE}: un
 * BYE è un avanzamento automatico, non una partita programmata presso un
 * edificio, e non deve essere replicato ai server locali.</p>
 *
 * @see TournamentMatch
 * @see Tournament
 */
public interface TournamentMatchOutboxPort {

    /**
     * Scrive atomicamente (nella transazione del chiamante) un singolo evento
     * outbox di tipo {@code "TOURNAMENT_MATCH_SCHEDULED"} per la partita
     * {@code SCHEDULED} indicata.
     *
     * @param match      la partita appena programmata (deve avere stato
     *                   {@link com.gameplatform.shared.domain.model.TournamentMatchStatus#SCHEDULED};
     *                   non deve essere una riga BYE); non deve essere {@code null}
     * @param tournament il torneo padre (fornisce il {@code gameType} per il payload
     *                   denormalizzato, poiché la partita non ha colonna game_type); non deve essere {@code null}
     * @throws IllegalArgumentException se {@code match} o {@code tournament} sono {@code null},
     *                                  se lo stato della partita non è {@code SCHEDULED} o se è una riga BYE
     */
    void publishScheduled(TournamentMatch match, Tournament tournament);
}
