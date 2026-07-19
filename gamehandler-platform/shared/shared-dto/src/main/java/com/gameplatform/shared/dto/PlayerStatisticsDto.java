package com.gameplatform.shared.dto;

import com.gameplatform.shared.domain.model.GameType;

import java.time.Instant;

/**
 * Rappresenta le statistiche aggregate di un giocatore per una singola tipologia di gioco.
 *
 * <p>Il DTO viene restituito sia dal read-model Central sia dal calcolo on-demand Locale,
 * garantendo che gli adapter REST espongano la medesima struttura dati ai client.</p>
 *
 * @param userId        l'identificativo del giocatore; non deve essere {@code null}
 * @param gameType      la tipologia di gioco cui si riferiscono le statistiche; non deve essere {@code null}
 * @param matchesPlayed il numero di partite completate a cui il giocatore ha partecipato; vale {@code 0} se non ne ha disputate
 * @param matchesWon    il numero di partite vinte tra quelle disputate; vale {@code 0} se non ne ha vinte
 * @param lastPlayedAt  l'istante dell'ultima partita giocata per questa tipologia; è {@code null} se il giocatore non ha mai giocato
 *
 * @see com.gameplatform.shared.domain.model.GameType
 */
public record PlayerStatisticsDto(
        String userId,
        GameType gameType,
        int matchesPlayed,
        int matchesWon,
        Instant lastPlayedAt
) {
}